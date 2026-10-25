// Teste de carga (k6) do Sistema de Gestão Jurídica.
// Cada usuário virtual (VU) faz login uma vez, renova o token antes de expirar e executa um mix realista:
// listagens paginadas, buscas, detalhes, resumos e escritas (criar/editar cliente e processo).
//
// Uso:  k6 run -e PERFIL=smoke -e SENHA=... tests/load/load.js     (veja README.md)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = (__ENV.BASE_URL || 'https://erpcorporativo.cloud').replace(/\/$/, '');
const API = `${BASE}/api/v1`;
const SENHA = __ENV.SENHA;
const USUARIOS = parseInt(__ENV.USUARIOS || '50', 10);
const PERFIL = __ENV.PERFIL || 'smoke';
const THINK = parseFloat(__ENV.THINK || '1'); // segundos médios entre ações; 0 = máxima vazão
const ESCRITA = parseFloat(__ENV.ESCRITA || '0.08'); // fração de iterações que escrevem

if (!SENHA) {
  throw new Error('Informe a senha dos usuários de teste: -e SENHA=...');
}

const PERFIS = {
  // 1) valida o script
  smoke: { executor: 'constant-vus', vus: 2, duration: '1m' },
  // 2) uso normal
  load: {
    executor: 'ramping-vus',
    stages: [
      { duration: '2m', target: 50 },
      { duration: '10m', target: 50 },
      { duration: '1m', target: 0 },
    ],
  },
  // 3) acha o ponto de quebra
  stress: {
    executor: 'ramping-vus',
    stages: [
      { duration: '2m', target: 50 }, { duration: '3m', target: 50 },
      { duration: '2m', target: 100 }, { duration: '3m', target: 100 },
      { duration: '2m', target: 200 }, { duration: '3m', target: 200 },
      { duration: '2m', target: 300 }, { duration: '3m', target: 300 },
      { duration: '2m', target: 400 }, { duration: '3m', target: 400 },
      { duration: '2m', target: 0 },
    ],
  },
  // 4) pico repentino
  spike: {
    executor: 'ramping-vus',
    stages: [
      { duration: '1m', target: 10 },
      { duration: '10s', target: 200 },
      { duration: '2m', target: 200 },
      { duration: '10s', target: 10 },
      { duration: '1m', target: 10 },
    ],
  },
  // 5) vazamento de memória/conexões
  soak: {
    executor: 'ramping-vus',
    stages: [
      { duration: '3m', target: 30 },
      { duration: '90m', target: 30 },
      { duration: '2m', target: 0 },
    ],
  },
};

if (!PERFIS[PERFIL]) {
  throw new Error(`PERFIL inválido: ${PERFIL}. Use: ${Object.keys(PERFIS).join(', ')}`);
}

export const options = {
  scenarios: { [PERFIL]: PERFIS[PERFIL] },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: false }],
    'http_req_duration{kind:leitura}': ['p(95)<500', 'p(99)<1500'],
    'http_req_duration{kind:busca}': ['p(95)<1500'],
    'http_req_duration{kind:escrita}': ['p(95)<1000'],
    'http_req_duration{kind:login}': ['p(95)<1500'],
  },
};

// 409/422 (documento ou número já existente) são respostas esperadas das escritas; qualquer outro 4xx/5xx é falha.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409, 422));

const clientesCriados = new Counter('clientes_criados');
const processosCriados = new Counter('processos_criados');
const reloginsPor401 = new Counter('relogins_por_401');

// ---------- estado por VU ----------
let token = null;
let tokenEm = 0;

const JSON_H = { 'Content-Type': 'application/json' };
const pad2 = (n) => String(n).padStart(2, '0');
const rnd = (n) => Math.floor(Math.random() * n);
const pick = (arr) => arr[rnd(arr.length)];

function auth(tags) {
  return { headers: { ...JSON_H, Authorization: `Bearer ${token}` }, tags };
}

function login() {
  const email = `carga${pad2(((__VU - 1) % USUARIOS) + 1)}@carga.teste`;
  const res = http.post(`${API}/auth/login`, JSON.stringify({ email, senha: SENHA }), {
    headers: JSON_H,
    tags: { name: 'login', kind: 'login' },
  });
  const ok = check(res, { 'login 200': (r) => r.status === 200 });
  token = ok ? res.json('accessToken') : null;
  tokenEm = Date.now();
  return ok;
}

function garantirToken() {
  if (!token) return login();
  if (Date.now() - tokenEm > 10 * 60 * 1000) {
    const res = http.post(`${API}/auth/refresh`, null, {
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
      tags: { name: 'refresh', kind: 'login' },
    });
    if (res.status === 200) {
      token = res.json('accessToken');
      tokenEm = Date.now();
      return true;
    }
    return login();
  }
  return true;
}

function get(path, name, kind = 'leitura') {
  let res = http.get(`${API}${path}`, auth({ name, kind }));
  if (res.status === 401) {
    reloginsPor401.add(1);
    if (login()) res = http.get(`${API}${path}`, auth({ name, kind }));
  }
  check(res, { [`${name} 200`]: (r) => r.status === 200 });
  return res;
}

// ---------- geradores de dados válidos ----------
function cpfAleatorio() {
  const d = Array.from({ length: 9 }, () => rnd(10));
  if (d.every((x) => x === d[0])) d[8] = (d[8] + 1) % 10;
  const dv = (base) => {
    const soma = base.reduce((s, x, i) => s + x * (base.length + 1 - i), 0);
    const r = (soma * 10) % 11;
    return r === 10 ? 0 : r;
  };
  d.push(dv(d));
  d.push(dv(d));
  return d.join('');
}

function numeroCnj() {
  const seq = String(rnd(10000000)).padStart(7, '0');
  const corpo = `${seq}2026826${'0100'}`; // seq(7) ano(4) J(1) TR(2) origem(4) = 18 dígitos
  let r = 0;
  for (const ch of `${corpo}00`) r = (r * 10 + (ch.charCodeAt(0) - 48)) % 97;
  const dv = String(98 - r).padStart(2, '0');
  return `${seq}-${dv}.2026.8.26.0100`;
}

// ---------- preparação: amostra dados reais do banco para buscas e detalhes ----------
export function setup() {
  const email = 'carga01@carga.teste';
  const res = http.post(`${API}/auth/login`, JSON.stringify({ email, senha: SENHA }), { headers: JSON_H });
  if (res.status !== 200) {
    throw new Error(`Login de preparação falhou (${res.status}). Os usuários de teste existem? Veja README.md.`);
  }
  const h = { headers: { ...JSON_H, Authorization: `Bearer ${res.json('accessToken')}` } };
  const base = http.get(`${API}/clientes?page=0&size=1`, h).json();
  const totalClientes = base.totalElements;
  const totalProcessos = http.get(`${API}/processos?page=0&size=1`, h).json('totalElements');
  const paginasClientes = Math.max(1, Math.ceil(totalClientes / 50));
  const paginasProcessos = Math.max(1, Math.ceil(totalProcessos / 50));

  const clientes = [];
  for (let i = 0; i < 20; i++) {
    const r = http.get(`${API}/clientes?page=${rnd(paginasClientes)}&size=50`, h).json('content') || [];
    r.forEach((c) => clientes.push({ id: c.id, nome: c.nome, documento: c.documento }));
  }
  const processos = [];
  for (let i = 0; i < 10; i++) {
    const r = http.get(`${API}/processos?page=${rnd(paginasProcessos)}&size=50`, h).json('content') || [];
    r.forEach((p) => processos.push({ id: p.id, numero: p.numero }));
  }
  console.log(`Base: ${totalClientes} clientes, ${totalProcessos} processos | amostra: ${clientes.length}/${processos.length}`);
  return { totalClientes, totalProcessos, clientes, processos };
}

// ---------- ações ----------
function listarClientes(dados) {
  const paginas = Math.max(1, Math.ceil(dados.totalClientes / 10));
  const r = Math.random();
  if (r < 0.7) return get(`/clientes?page=${rnd(10)}&size=10`, 'GET /clientes (paginas iniciais)');
  if (r < 0.85) return get(`/clientes?page=${rnd(paginas)}&size=10`, 'GET /clientes (pagina profunda)');
  return get(`/clientes?ativo=true&tipo=${pick(['PF', 'PJ'])}&uf=${pick(['SP', 'RJ', 'MG', 'PR'])}&page=${rnd(50)}&size=20`,
    'GET /clientes (filtros)');
}

function buscarClientes(dados) {
  const c = pick(dados.clientes);
  if (Math.random() < 0.5) {
    return get(`/clientes?busca=${encodeURIComponent(c.nome)}`, 'GET /clientes?busca=nome', 'busca');
  }
  const trecho = String(c.documento).replace(/\D/g, '').slice(0, 8);
  return get(`/clientes?busca=${trecho}`, 'GET /clientes?busca=documento', 'busca');
}

function listarProcessos(dados) {
  const r = Math.random();
  if (r < 0.5) {
    return get(`/processos?situacao=${pick(['EM_ANDAMENTO', 'PENDENTE', 'URGENTE'])}&page=${rnd(30)}&size=10`,
      'GET /processos (situacao)');
  }
  if (r < 0.8) {
    return get(`/processos?tipo=${pick(['CIVIL', 'TRABALHISTA', 'FAMILIA', 'BANCARIO'])}&page=${rnd(30)}&size=10`,
      'GET /processos (tipo)');
  }
  return get(`/processos?clienteId=${pick(dados.clientes).id}`, 'GET /processos?clienteId');
}

function escrever(dados) {
  const sufixo = `${__VU}-${__ITER}-${rnd(100000)}`;
  const cliente = {
    tipo: 'PF', nome: `Teste K6 ${sufixo}`, documento: cpfAleatorio(), email: `k6.${sufixo}@carga.teste`,
    telefone: '11999990000', cep: '01001-000', logradouro: 'Praça da Sé', numero: '1', bairro: 'Sé',
    cidade: 'São Paulo', uf: 'SP', observacoes: 'CARGA_K6',
  };
  let res = http.post(`${API}/clientes`, JSON.stringify(cliente), auth({ name: 'POST /clientes', kind: 'escrita' }));
  if (res.status !== 201) return;
  clientesCriados.add(1);
  const id = res.json('id');

  res = http.put(`${API}/clientes/${id}`, JSON.stringify({ ...cliente, nome: `${cliente.nome} (editado)` }),
    auth({ name: 'PUT /clientes/{id}', kind: 'escrita' }));
  check(res, { 'PUT cliente 200': (r) => r.status === 200 });

  const processo = {
    numero: numeroCnj(), clienteId: id, tipo: pick(['CIVIL', 'TRABALHISTA', 'FAMILIA']),
    areaJuridica: 'Direito Civil', tribunal: 'TJSP', comarca: 'São Paulo', vara: '1ª Vara',
    advogadoNome: 'Dr. K6', advogadoOab: 'OAB/SP 123456', situacao: 'EM_ANDAMENTO', observacoes: 'CARGA_K6',
  };
  res = http.post(`${API}/processos`, JSON.stringify(processo), auth({ name: 'POST /processos', kind: 'escrita' }));
  if (res.status === 201) {
    processosCriados.add(1);
    get(`/processos/${res.json('id')}`, 'GET /processos/{id}');
  }
}

export default function (dados) {
  if (!garantirToken()) {
    sleep(2);
    return;
  }
  const r = Math.random();
  if (r < ESCRITA) escrever(dados);
  else if (r < 0.40) listarClientes(dados);
  else if (r < 0.55) buscarClientes(dados);
  else if (r < 0.68) get(`/clientes/${pick(dados.clientes).id}`, 'GET /clientes/{id}');
  else if (r < 0.85) listarProcessos(dados);
  else if (r < 0.93) get(`/processos/${pick(dados.processos).id}`, 'GET /processos/{id}');
  else get(Math.random() < 0.5 ? '/clientes/resumo' : '/processos/resumo', 'GET /resumo');

  if (THINK > 0) sleep(THINK * (0.5 + Math.random()));
}
