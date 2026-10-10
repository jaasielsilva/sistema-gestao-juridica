import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Validador de grupo: o campo `confirmar` precisa ser igual ao campo indicado (padrao: novaSenha / senha). */
export function passwordsMatch(field: string, confirmField = 'confirmar') {
  return (group: AbstractControl): ValidationErrors | null => {
    const a = group.get(field)?.value as string | undefined;
    const b = group.get(confirmField)?.value as string | undefined;
    return a && b && a !== b ? { mismatch: true } : null;
  };
}
