import type { Plugin } from 'vite';
import { defineConfig } from 'vitest/config';

/**
 * Development only: answers /widget.js with a small classic script that loads the TypeScript source as a module,
 * so the same embed snippet the panel shows works on any local page while editing the widget.
 */
function devWidgetScript(): Plugin {
  return {
    name: 'dev-widget-script',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/widget.js', (_request, response) => {
        response.setHeader('Content-Type', 'text/javascript');
        response.setHeader('Cache-Control', 'no-store');
        response.end(`(() => {
  const tag = document.currentScript;
  const module = document.createElement('script');
  module.type = 'module';
  module.src = new URL('/src/main.ts', tag.src).href;
  document.head.append(module);
})();`);
      });
    },
  };
}

/**
 * Build only: drops the indentation and spaces the stylesheet keeps for reading. The minifier leaves template strings
 * alone, and the stylesheet is a third of the script.
 */
function compactStyles(): Plugin {
  return {
    name: 'compact-styles',
    apply: 'build',
    transform(code, id) {
      if (!id.endsWith('/src/styles.ts')) {
        return null;
      }
      return code.replace(/`([^`]*)`/, (_match, css: string) => {
        const compact = css
          .replace(/\s+/g, ' ')
          .replace(/\s*([{};,>])\s*/g, '$1')
          .replace(/:\s/g, ':')
          .replace(/;}/g, '}')
          .trim();
        return `\`${compact}\``;
      });
    },
  };
}

// Builds one self-contained classic script (dist/widget.js) that sites load with a <script> tag. A classic script,
// not a module, so document.currentScript tells the widget which tag loaded it and with which data attributes.
export default defineConfig({
  plugins: [devWidgetScript(), compactStyles()],
  server: {
    port: 5174,
    strictPort: true,
  },
  preview: {
    port: 5174,
    strictPort: true,
  },
  build: {
    target: 'es2020',
    lib: {
      entry: 'src/main.ts',
      name: 'SupportChatWidget',
      formats: ['iife'],
      fileName: () => 'widget.js',
    },
    // The demo page is for local development only and must not ship with the script
    copyPublicDir: false,
  },
  test: {
    environment: 'jsdom',
  },
});
