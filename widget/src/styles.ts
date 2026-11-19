// Lives inside the shadow root: the host page's CSS cannot reach these elements and these rules cannot leak out.
export const styles = `
:host {
  all: initial;
  --sc-primary: #4f46e5;
  --sc-on-primary: #ffffff;
  --sc-bg: #ffffff;
  --sc-surface: #f4f4f7;
  --sc-text: #16161d;
  --sc-muted: #6b6b7b;
  --sc-border: #e4e4ea;
  --sc-error: #b42318;
  --sc-shadow: 0 24px 60px -12px rgba(17, 17, 40, .28), 0 4px 14px rgba(17, 17, 40, .08);
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 2147483000;
  font: 14px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  color: var(--sc-text);
  -webkit-font-smoothing: antialiased;
}
:host([data-position="left"]) { right: auto; left: 20px; }
@media (prefers-color-scheme: dark) {
  :host {
    --sc-bg: #17171f;
    --sc-surface: #24242f;
    --sc-text: #f2f2f6;
    --sc-muted: #a0a0b0;
    --sc-border: #33333f;
    --sc-error: #fda29b;
    --sc-shadow: 0 24px 60px -12px rgba(0, 0, 0, .7), 0 0 0 1px rgba(255, 255, 255, .04);
  }
}
*, *::before, *::after { box-sizing: border-box; }
button, textarea { font: inherit; color: inherit; }
button:focus-visible, .input:focus-visible { outline: 2px solid var(--sc-primary); outline-offset: 2px; }
.launcher {
  width: 58px; height: 58px; border-radius: 50%; border: 0; cursor: pointer;
  background: var(--sc-primary); color: var(--sc-on-primary);
  display: grid; place-items: center;
  box-shadow: 0 10px 28px -6px rgba(17, 17, 40, .45);
  transition: transform .2s cubic-bezier(.2, .8, .2, 1), box-shadow .2s;
}
.launcher:hover { transform: translateY(-2px) scale(1.04); box-shadow: 0 14px 32px -6px rgba(17, 17, 40, .5); }
.launcher:active { transform: scale(.96); }
.launcher svg { width: 26px; height: 26px; }
.panel {
  position: absolute; bottom: 74px; right: 0;
  width: 380px; height: min(600px, calc(100vh - 112px));
  display: flex; flex-direction: column;
  background: var(--sc-bg); border: 1px solid var(--sc-border); border-radius: 18px;
  box-shadow: var(--sc-shadow); overflow: hidden;
  transform-origin: bottom right; animation: sc-open .22s cubic-bezier(.2, .8, .2, 1);
}
:host([data-position="left"]) .panel { right: auto; left: 0; transform-origin: bottom left; }
.panel[hidden] { display: none; }
@keyframes sc-open { from { opacity: 0; transform: translateY(10px) scale(.97); } }
.header {
  display: flex; align-items: center; gap: 11px;
  padding: 14px 12px 14px 16px; background: var(--sc-primary); color: var(--sc-on-primary);
  background-image: linear-gradient(135deg, rgba(255, 255, 255, .14), rgba(0, 0, 0, .1));
}
.avatar {
  width: 36px; height: 36px; flex: none; border-radius: 11px; display: grid; place-items: center;
  background: rgba(255, 255, 255, .18); box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .25);
}
.avatar svg { width: 18px; height: 18px; }
.identity { flex: 1; min-width: 0; }
.title { margin: 0; font-size: 15px; font-weight: 650; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.subtitle { margin: 1px 0 0; font-size: 12px; opacity: .85; display: flex; align-items: center; gap: 6px; }
.subtitle::before { content: ""; width: 7px; height: 7px; border-radius: 50%; background: #4ade80; box-shadow: 0 0 0 2px rgba(255, 255, 255, .25); }
.close {
  border: 0; background: transparent; color: inherit; cursor: pointer; flex: none;
  width: 34px; height: 34px; border-radius: 10px; display: grid; place-items: center; transition: background .15s;
}
.close:hover { background: rgba(255, 255, 255, .16); }
.close:focus-visible { outline-color: var(--sc-on-primary); outline-offset: -2px; }
.close svg { width: 18px; height: 18px; }
.log {
  flex: 1; overflow-y: auto; padding: 18px 14px; display: flex; flex-direction: column; gap: 10px;
  scrollbar-width: thin; scrollbar-color: var(--sc-border) transparent;
}
.msg {
  max-width: 84%; padding: 10px 13px; border-radius: 16px; white-space: pre-wrap; overflow-wrap: anywhere;
  animation: sc-in .2s ease-out;
}
@keyframes sc-in { from { opacity: 0; transform: translateY(4px); } }
.msg.visitor { align-self: flex-end; background: var(--sc-primary); color: var(--sc-on-primary); border-bottom-right-radius: 5px; }
.msg.assistant, .msg.agent { align-self: flex-start; background: var(--sc-surface); border-bottom-left-radius: 5px; }
.msg.failed { color: var(--sc-error); }
.msg.pending .body:empty::after {
  content: attr(data-thinking); color: var(--sc-muted);
  animation: sc-pulse 1.4s ease-in-out infinite;
}
@keyframes sc-pulse { 50% { opacity: .45; } }
.msg.agent { box-shadow: inset 3px 0 0 var(--sc-primary); }
.msg.agent .author { font-size: 12px; font-weight: 650; color: var(--sc-primary); margin-bottom: 2px; }
.event, .notice { align-self: center; font-size: 12px; color: var(--sc-muted); text-align: center; padding: 3px 10px; }
.event { background: var(--sc-surface); border-radius: 999px; }
.notice.error { color: var(--sc-error); }
.verdict { display: flex; gap: 4px; margin-top: 6px; }
.verdict button, .rating button { border: 1px solid transparent; background: none; cursor: pointer; border-radius: 8px; line-height: 1; }
.verdict button { min-width: 28px; min-height: 26px; padding: 3px 5px; font-size: 13px; opacity: .7; }
.verdict button:hover, .verdict button[aria-pressed="true"] { opacity: 1; border-color: var(--sc-border); background: var(--sc-bg); }
.rating { display: flex; align-items: center; justify-content: center; gap: 2px; padding: 8px 12px 0; font-size: 12px; color: var(--sc-muted); }
.rating[hidden] { display: none; }
.rating span { margin-right: 6px; }
.rating button { min-width: 26px; min-height: 26px; padding: 2px 3px; font-size: 18px; color: var(--sc-muted); }
.rating:has(button:hover) button:not(:hover ~ button), .rating button:focus-visible { color: var(--sc-primary); opacity: 1; }
.handoff { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 12px 0; flex-wrap: wrap; }
.handoff-status { margin: 0; font-size: 12px; color: var(--sc-muted); flex: 1; min-width: 150px; }
.handoff-status[hidden], .handoff-button[hidden] { display: none; }
.handoff-button {
  margin-left: auto; border: 1px solid var(--sc-border); background: var(--sc-bg); cursor: pointer;
  border-radius: 999px; padding: 5px 12px; font-size: 12px; font-weight: 550; transition: border-color .15s, color .15s;
}
.handoff-button:hover:not(:disabled) { border-color: var(--sc-primary); color: var(--sc-primary); }
.handoff-button:disabled { opacity: .5; cursor: default; }
.action { align-self: flex-start; max-width: 85%; border: 1px solid var(--sc-border); border-radius: 12px; padding: 9px 11px; font-size: 13px; }
.action ul { margin: 4px 0 8px; padding-left: 16px; color: var(--sc-muted); }
.action-buttons { display: flex; gap: 6px; }
.action-buttons .handoff-button { margin: 0; }
.action-buttons .confirm { background: var(--sc-primary); color: var(--sc-on-primary); border-color: var(--sc-primary); }
.cite {
  border: 0; background: transparent; padding: 0 1px; cursor: pointer;
  color: var(--sc-primary); font-size: 11px; font-weight: 700; vertical-align: super; line-height: 0;
}
.sources { margin-top: 9px; padding-top: 8px; border-top: 1px solid var(--sc-border); white-space: normal; font-size: 12px; }
.sources-title { margin: 0 0 4px; color: var(--sc-muted); font-weight: 600; text-transform: uppercase; letter-spacing: .04em; font-size: 11px; }
.sources ol { margin: 0; padding-left: 18px; }
.sources li { margin: 3px 0; }
.sources details summary { cursor: pointer; }
.sources details p { margin: 4px 0 0; color: var(--sc-muted); }
.sources li.highlight summary { color: var(--sc-primary); font-weight: 600; }
.composer { display: flex; align-items: flex-end; gap: 8px; margin: 10px 12px 12px; padding: 5px 5px 5px 4px; border: 1px solid var(--sc-border); border-radius: 14px; background: var(--sc-bg); transition: border-color .15s, box-shadow .15s; }
.composer:focus-within { border-color: var(--sc-primary); box-shadow: 0 0 0 3px color-mix(in srgb, var(--sc-primary) 18%, transparent); }
.input {
  flex: 1; resize: none; max-height: 120px; min-height: 36px;
  padding: 7px 9px; border: 0; background: transparent; outline: none;
}
.input::placeholder { color: var(--sc-muted); }
.send {
  width: 36px; height: 36px; flex: none; border: 0; border-radius: 10px; cursor: pointer;
  background: var(--sc-primary); color: var(--sc-on-primary); display: grid; place-items: center; transition: opacity .15s, transform .15s;
}
.send:hover:not(:disabled) { transform: translateY(-1px); }
.send:disabled { opacity: .4; cursor: default; }
.send svg { width: 17px; height: 17px; }
.counter { padding: 0 14px 8px; margin-top: -6px; font-size: 11px; color: var(--sc-muted); text-align: right; }
.counter[hidden] { display: none; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
@media (max-width: 480px) {
  .panel { position: fixed; inset: 0; width: auto; height: auto; border-radius: 0; border: 0; }
}
/* High contrast: the system drops backgrounds, so round buttons and bubbles keep an outline to be seen */
@media (forced-colors: active) {
  .launcher, .send, .close, .msg, .rating button, .verdict button { border: 1px solid CanvasText; }
  .send:disabled { border-color: GrayText; }
}
@media (prefers-reduced-motion: reduce) {
  .launcher, .panel, .msg, .msg.pending .body:empty::after { transition: none; animation: none; }
}
`;
