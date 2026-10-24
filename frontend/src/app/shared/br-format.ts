import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Utilitarios de CPF/CNPJ/CEP/telefone/CNJ: mascara de exibicao e validacao dos digitos verificadores. */

export const onlyDigits = (v: string | null | undefined): string => (v ?? '').replace(/\D/g, '');

export function formatCpf(v: string): string {
  const d = onlyDigits(v).slice(0, 11);
  return d
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1-$2');
}

export function formatCnpj(v: string): string {
  const d = onlyDigits(v).slice(0, 14);
  return d
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1/$2')
    .replace(/(\d{4})(\d)/, '$1-$2');
}

export function formatDocumento(tipo: 'PF' | 'PJ', v: string): string {
  return tipo === 'PF' ? formatCpf(v) : formatCnpj(v);
}

export function formatCep(v: string): string {
  const d = onlyDigits(v).slice(0, 8);
  return d.replace(/^(\d{5})(\d)/, '$1-$2');
}

export function formatTelefone(v: string): string {
  const d = onlyDigits(v).slice(0, 11);
  if (d.length <= 2) return d;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

/** Formata número CNJ no padrão NNNNNNN-DD.AAAA.J.TR.OOOO (20 dígitos). */
export function formatCnj(v: string): string {
  const d = onlyDigits(v).slice(0, 20);
  return d
    .replace(/^(\d{7})(\d)/, '$1-$2')
    .replace(/^(\d{7}-\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{7}-\d{2}\.\d{4})(\d)/, '$1.$2')
    .replace(/^(\d{7}-\d{2}\.\d{4}\.\d{1})(\d)/, '$1.$2')
    .replace(/^(\d{7}-\d{2}\.\d{4}\.\d{1}\.\d{2})(\d)/, '$1.$2');
}

function checkDigit(d: string, weights: number[]): number {
  const sum = weights.reduce((acc, w, i) => acc + Number(d[i]) * w, 0);
  const r = (sum * 10) % 11;
  return r === 10 ? 0 : r;
}

export function isValidCpf(value: string): boolean {
  const d = onlyDigits(value);
  if (d.length !== 11 || /^(\d)\1+$/.test(d)) return false;
  const w1 = [10, 9, 8, 7, 6, 5, 4, 3, 2];
  const w2 = [11, 10, 9, 8, 7, 6, 5, 4, 3, 2];
  return checkDigit(d, w1) === Number(d[9]) && checkDigit(d, w2) === Number(d[10]);
}

export function isValidCnpj(value: string): boolean {
  const d = onlyDigits(value);
  if (d.length !== 14 || /^(\d)\1+$/.test(d)) return false;
  const w1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
  const w2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
  return checkDigit(d, w1) === Number(d[12]) && checkDigit(d, w2) === Number(d[13]);
}

/** Validação de numeração única CNJ (Resolução 65/2008, ISO 7064 MOD 97-10). */
export function isValidCnj(value: string): boolean {
  const d = onlyDigits(value);
  if (d.length !== 20) return false;
  try {
    const toVerify = d.slice(0, 7) + d.slice(9) + d.slice(7, 9);
    return BigInt(toVerify) % 97n === 1n;
  } catch {
    return false;
  }
}

/** Valida o campo conforme o tipo de pessoa devolvido por `tipo()` (campo vazio fica por conta do `required`). */
export function documentoValidator(tipo: () => 'PF' | 'PJ'): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string;
    if (!value) return null;
    const ok = tipo() === 'PF' ? isValidCpf(value) : isValidCnpj(value);
    return ok ? null : { documento: true };
  };
}

export const cnjValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value as string;
  if (!value) return null;
  return isValidCnj(value) ? null : { cnj: true };
};

export const telefoneValidator: ValidatorFn = (control) => {
  const len = onlyDigits(control.value as string).length;
  return len === 0 || len === 10 || len === 11 ? null : { telefone: true };
};

export const cepValidator: ValidatorFn = (control) => {
  const len = onlyDigits(control.value as string).length;
  return len === 0 || len === 8 ? null : { cep: true };
};
