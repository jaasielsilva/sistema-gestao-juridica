import {
  formatCep,
  formatCnj,
  formatCnpj,
  formatCpf,
  formatDocumento,
  formatTelefone,
  isValidCnj,
  isValidCnpj,
  isValidCpf,
  onlyDigits,
} from './br-format';

describe('br-format', () => {
  it('remove tudo que nao e digito', () => {
    expect(onlyDigits('529.982.247-25')).toBe('52998224725');
    expect(onlyDigits(null)).toBe('');
  });

  it('formata CPF e CNPJ progressivamente', () => {
    expect(formatCpf('52998224725')).toBe('529.982.247-25');
    expect(formatCpf('5299')).toBe('529.9');
    expect(formatCnpj('11222333000181')).toBe('11.222.333/0001-81');
    expect(formatCnpj('112223')).toBe('11.222.3');
    expect(formatDocumento('PF', '52998224725')).toBe('529.982.247-25');
    expect(formatDocumento('PJ', '11222333000181')).toBe('11.222.333/0001-81');
  });

  it('formata CEP e telefone fixo e celular', () => {
    expect(formatCep('01310100')).toBe('01310-100');
    expect(formatTelefone('1133334444')).toBe('(11) 3333-4444');
    expect(formatTelefone('11987654321')).toBe('(11) 98765-4321');
    expect(formatTelefone('11')).toBe('11');
  });

  it('valida CPF pelos digitos verificadores', () => {
    expect(isValidCpf('529.982.247-25')).toBe(true);
    expect(isValidCpf('529.982.247-24')).toBe(false);
    expect(isValidCpf('111.111.111-11')).toBe(false);
    expect(isValidCpf('123')).toBe(false);
  });

  it('valida CNPJ pelos digitos verificadores', () => {
    expect(isValidCnpj('11.222.333/0001-81')).toBe(true);
    expect(isValidCnpj('11.222.333/0001-80')).toBe(false);
    expect(isValidCnpj('00.000.000/0000-00')).toBe(false);
    expect(isValidCnpj('529.982.247-25')).toBe(false);
  });

  it('formata e valida CNJ', () => {
    expect(formatCnj('00012347120248260100')).toBe('0001234-71.2024.8.26.0100');
    expect(isValidCnj('0001234-71.2024.8.26.0100')).toBe(true);
    expect(isValidCnj('0000009-15.2015.8.26.0558')).toBe(true);
    expect(isValidCnj('0000009-16.2015.8.26.0558')).toBe(false);
    expect(isValidCnj('12345')).toBe(false);
  });
});
