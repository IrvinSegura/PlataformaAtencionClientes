// Where the end-to-end run finds each part. Ports apart from the development ones (8080, 5173, 5174), so a run
// does not collide with servers already open on this machine
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8097';
export const PANEL_URL = process.env.E2E_PANEL_URL ?? 'http://localhost:5183';
export const WIDGET_URL = process.env.E2E_WIDGET_URL ?? 'http://localhost:5184';
