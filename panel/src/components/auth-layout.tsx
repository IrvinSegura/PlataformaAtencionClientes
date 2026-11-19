import type { ReactNode } from 'react';
import { LEGAL_LINKS } from '../api/identity';
import { Icons } from './icons';

const FEATURES = [
  {
    icon: Icons.Book,
    title: 'Responde con tus documentos',
    text: 'Sube preguntas frecuentes y políticas; cada respuesta cita su fuente.',
  },
  {
    icon: Icons.Shield,
    title: 'Dice «no sé» en lugar de inventar',
    text: 'Lo que no está en tu base de conocimiento se convierte en una tarea pendiente.',
  },
  {
    icon: Icons.Headset,
    title: 'Tu equipo entra cuando hace falta',
    text: 'El visitante pide una persona y la conversación llega en vivo al panel.',
  },
];

/**
 * The box a new account ticks to accept the terms of service, with both legal pages a click away. Uncontrolled with a
 * name inside a plain form, or controlled.
 */
export function TermsCheckbox({
  name,
  checked,
  onChange,
}: {
  name?: string;
  checked?: boolean;
  onChange?: (checked: boolean) => void;
}) {
  return (
    <label className="row terms-check">
      <input
        type="checkbox"
        name={name}
        required
        checked={checked}
        onChange={onChange && ((event) => onChange(event.target.checked))}
      />
      <span>
        Acepto los{' '}
        <a href={LEGAL_LINKS.terms} target="_blank" rel="noreferrer">
          términos y condiciones
        </a>{' '}
        y la{' '}
        <a href={LEGAL_LINKS.privacy} target="_blank" rel="noreferrer">
          política de privacidad
        </a>
        .
      </span>
    </label>
  );
}

/** Sign-in, registration and invitations: the product on one side, the form on the other. */
export function AuthLayout({
  title,
  lead,
  children,
  footer,
}: {
  title: string;
  lead?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="auth">
      <aside className="auth-brand">
        <div className="auth-logo">
          <span className="logo-mark">
            <Icons.Chat size={17} />
          </span>
          Atención con IA
        </div>
        <div className="auth-pitch">
          <h2>Soporte que responde al instante, sin inventar.</h2>
          <p>Un asistente entrenado con lo que tu empresa ya sabe, y tu equipo a un clic cuando el cliente lo necesita.</p>
          <ul className="auth-features">
            {FEATURES.map(({ icon: Icon, title: featureTitle, text }) => (
              <li key={featureTitle}>
                <span className="feature-icon">
                  <Icon size={16} />
                </span>
                <span>
                  <strong>{featureTitle}</strong>
                  {text}
                </span>
              </li>
            ))}
          </ul>
        </div>
        <div className="auth-foot">Tus datos se aíslan por espacio de trabajo.</div>
      </aside>
      <main className="auth-main">
        <div className="auth-card">
          <h1>{title}</h1>
          {lead && <p className="auth-lead">{lead}</p>}
          {children}
          {footer && <div className="auth-footer">{footer}</div>}
          <p className="auth-legal">
            <a href={LEGAL_LINKS.terms} target="_blank" rel="noreferrer">
              Términos y condiciones
            </a>
            {' · '}
            <a href={LEGAL_LINKS.privacy} target="_blank" rel="noreferrer">
              Política de privacidad
            </a>
          </p>
        </div>
      </main>
    </div>
  );
}
