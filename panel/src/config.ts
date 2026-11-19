// Build-time settings; see .env.example
export const config = {
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, ''),
  widgetScriptUrl: import.meta.env.VITE_WIDGET_SCRIPT_URL ?? '',
  widgetApiUrl: import.meta.env.VITE_WIDGET_API_URL ?? '',
  widgetDemoUrl: import.meta.env.VITE_WIDGET_DEMO_URL ?? '',
};
