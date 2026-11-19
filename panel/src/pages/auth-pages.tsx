import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../auth/auth-context';
import { AuthLayout, TermsCheckbox } from '../components/auth-layout';
import { ErrorAlert } from '../components/ui';

// Same rule as the backend's PasswordPolicy; checked here only to answer before the request
const MIN_PASSWORD_LENGTH = 12;

function useSubmit(action: (form: FormData) => Promise<void>) {
  const [error, setError] = useState<unknown>(null);
  const [pending, setPending] = useState(false);
  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      // On success the session changes and the route guard leaves this page
      await action(new FormData(event.currentTarget));
    } catch (caught) {
      setError(caught);
      setPending(false);
    }
  };
  return { error, pending, onSubmit };
}

export function LoginPage() {
  const { login } = useAuth();
  const { error, pending, onSubmit } = useSubmit((form) =>
    login(String(form.get('email')), String(form.get('password'))),
  );
  return (
    <AuthLayout
      title="Iniciar sesión"
      lead="Bienvenido de nuevo. Entra para ver las conversaciones de hoy."
      footer={
        <>
          ¿No tienes cuenta? <Link to="/register">Crea tu espacio de trabajo</Link>
        </>
      }
    >
      <form className="form" onSubmit={onSubmit}>
        <label>
          Email
          <input name="email" type="email" autoComplete="username" required maxLength={254} placeholder="tu@empresa.com" />
        </label>
        <label>
          Contraseña
          <input name="password" type="password" autoComplete="current-password" required maxLength={256} />
        </label>
        <ErrorAlert error={error} />
        <button type="submit" className="button button-primary" disabled={pending}>
          {pending ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </AuthLayout>
  );
}

export function RegisterPage() {
  const { register } = useAuth();
  const { error, pending, onSubmit } = useSubmit((form) =>
    register({
      email: String(form.get('email')),
      password: String(form.get('password')),
      displayName: String(form.get('displayName')),
      workspaceName: String(form.get('workspaceName')),
      acceptTerms: form.get('acceptTerms') === 'on',
    }),
  );
  return (
    <AuthLayout
      title="Crear espacio de trabajo"
      lead="Tu asistente queda listo en minutos: crea la cuenta, sube un documento e instala el widget."
      footer={
        <>
          ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
        </>
      }
    >
      <form className="form" onSubmit={onSubmit}>
        <label>
          Nombre de tu empresa
          <input name="workspaceName" required maxLength={80} autoComplete="organization" />
        </label>
        <label>
          Tu nombre
          <input name="displayName" required maxLength={80} autoComplete="name" />
        </label>
        <label>
          Email
          <input name="email" type="email" required maxLength={254} autoComplete="username" />
        </label>
        <label>
          Contraseña
          <input
            name="password"
            type="password"
            required
            minLength={MIN_PASSWORD_LENGTH}
            autoComplete="new-password"
            aria-describedby="password-hint"
          />
          <span id="password-hint" className="hint">
            Al menos {MIN_PASSWORD_LENGTH} caracteres. Una frase de varias palabras es fácil de recordar y segura.
          </span>
        </label>
        <TermsCheckbox name="acceptTerms" />
        <ErrorAlert error={error} />
        <button type="submit" className="button button-primary" disabled={pending}>
          {pending ? 'Creando…' : 'Crear cuenta'}
        </button>
      </form>
    </AuthLayout>
  );
}
