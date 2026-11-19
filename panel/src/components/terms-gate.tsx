import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { authApi, LEGAL_LINKS } from '../api/identity';
import { useAuth } from '../auth/auth-context';
import { ErrorAlert } from './ui';

/**
 * Stands in for every page until the operator accepts the terms of service in force: someone who joined before they
 * existed, or before they last changed. Signing out is the way to decline.
 */
export function TermsGate({ version }: { version: string }) {
  const { logout } = useAuth();
  const client = useQueryClient();
  const [checked, setChecked] = useState(false);
  const accept = useMutation({
    mutationFn: () => authApi.acceptTerms(version),
    onSuccess: (profile) => client.setQueryData(['me'], profile),
  });
  return (
    <section className="card terms-gate" aria-labelledby="terms-gate-title">
      <h1 id="terms-gate-title" className="card-title">
        Términos y condiciones
      </h1>
      <p>
        Para seguir usando el panel, lee y acepta los{' '}
        <a href={LEGAL_LINKS.terms} target="_blank" rel="noreferrer">
          términos y condiciones
        </a>{' '}
        vigentes. Explican cómo funcionan los planes y pagos, qué puedes esperar de las respuestas del asistente y quién
        responde por los datos de tus clientes, como detalla la{' '}
        <a href={LEGAL_LINKS.privacy} target="_blank" rel="noreferrer">
          política de privacidad
        </a>
        .
      </p>
      <form
        className="form"
        onSubmit={(event) => {
          event.preventDefault();
          accept.mutate();
        }}
      >
        <label className="row terms-check">
          <input type="checkbox" required checked={checked} onChange={(event) => setChecked(event.target.checked)} />
          <span>He leído y acepto los términos y condiciones.</span>
        </label>
        <ErrorAlert error={accept.error} />
        <div className="row">
          <button type="submit" className="button button-primary" disabled={!checked || accept.isPending}>
            {accept.isPending ? 'Guardando…' : 'Aceptar y continuar'}
          </button>
          <button type="button" className="button button-ghost" onClick={() => void logout()}>
            Cerrar sesión
          </button>
        </div>
      </form>
    </section>
  );
}
