import { ChatWidget } from './widget';

/*
 * Entry point of widget.js. Sites embed it with:
 *
 *   <script src="https://cdn.example.com/widget.js" data-widget-key="..." data-api-url="https://api.example.com" async></script>
 *
 * Optional attributes: data-position="left" and data-primary-color="#0f766e".
 */
declare global {
  interface Window {
    __supportChatWidget?: ChatWidget;
  }
}

function scriptTag(): HTMLScriptElement | null {
  // currentScript is null for module scripts (the local demo page), so fall back to finding the tag
  const current = document.currentScript;
  if (current instanceof HTMLScriptElement && current.dataset.widgetKey) {
    return current;
  }
  return document.querySelector<HTMLScriptElement>('script[data-widget-key]');
}

function boot(script: HTMLScriptElement): void {
  // A site that includes the snippet twice still gets one widget
  if (window.__supportChatWidget) {
    return;
  }
  const widgetKey = script.dataset.widgetKey?.trim();
  const apiUrl = script.dataset.apiUrl?.trim() || new URL(script.src, document.baseURI).origin;
  if (!widgetKey) {
    console.warn('[support-chat] Missing data-widget-key on the widget script tag');
    return;
  }
  const position = script.dataset.position === 'left' ? 'left' : 'right';
  const primaryColor = script.dataset.primaryColor;
  const widget = new ChatWidget({ apiUrl, widgetKey, position, ...(primaryColor ? { primaryColor } : {}) });
  window.__supportChatWidget = widget;
  widget.mount();
}

// Read the tag now: currentScript is only set while this script runs, not later in a DOMContentLoaded handler
const script = scriptTag();
if (script === null) {
  console.warn('[support-chat] Widget script tag not found');
} else if (document.body) {
  boot(script);
} else {
  document.addEventListener('DOMContentLoaded', () => boot(script), { once: true });
}
