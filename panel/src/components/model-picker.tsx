import { useQuery } from '@tanstack/react-query';
import { assistantApi, type ModelPreference } from '../api/conversations';
import { Badge } from './ui';

export const MODEL_CHOICES: Record<ModelPreference, { label: string; text: string }> = {
  AUTOMATIC: {
    label: 'Automático',
    text: 'Usa el rápido o el preciso según cada pregunta.',
  },
  FAST: {
    label: 'Rápido',
    text: 'Responde antes y cuesta menos. Ideal para preguntas frecuentes.',
  },
  PRECISE: {
    label: 'Preciso',
    text: 'El modelo más capaz, para preguntas complejas. Cada respuesta cuesta más.',
  },
};

const ORDER: ModelPreference[] = ['AUTOMATIC', 'FAST', 'PRECISE'];

/** Lists the models behind each choice, as the server has them configured. */
export function useModelChoices() {
  return useQuery({ queryKey: ['assistant-models'], queryFn: assistantApi.models, staleTime: 5 * 60_000 });
}

/** The model a channel answers with, drawn as three cards. */
export function ModelPicker({
  value,
  onChange,
  name,
  disabled = false,
}: {
  value: ModelPreference;
  onChange: (value: ModelPreference) => void;
  name: string;
  disabled?: boolean;
}) {
  const choices = useModelChoices();
  const models = new Map(choices.data?.map((choice) => [choice.preference, choice.models]) ?? []);
  return (
    <fieldset className="choices" disabled={disabled}>
      <legend className="sr-only">Modelo</legend>
      {ORDER.map((preference) => {
        const behind = models.get(preference);
        return (
          <label className="choice" key={preference}>
            <input
              type="radio"
              name={name}
              value={preference}
              checked={value === preference}
              onChange={() => onChange(preference)}
            />
            <span className="choice-title">
              {MODEL_CHOICES[preference].label}
              {preference === 'AUTOMATIC' && <Badge tone="primary">Recomendado</Badge>}
            </span>
            <span className="choice-text">{MODEL_CHOICES[preference].text}</span>
            {behind && behind.length > 0 && <span className="choice-models">{behind.join(' · ')}</span>}
          </label>
        );
      })}
    </fieldset>
  );
}
