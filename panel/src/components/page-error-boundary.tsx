import { Component, type ErrorInfo, type ReactNode } from 'react';

/** @param resetKey when it changes, a failed page is tried again; pass the path */
type Props = { children: ReactNode; resetKey: string };
type State = { failed: boolean };

/**
 * Catches a page that fails while it renders, usually because its code could not be downloaded: after a deploy the
 * old files are gone, and reloading picks up the new ones. Moving to another page tries again.
 */
export class PageErrorBoundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidUpdate(previous: Props) {
    if (this.state.failed && previous.resetKey !== this.props.resetKey) {
      this.setState({ failed: false });
    }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('A page failed to render', error, info.componentStack);
  }

  render() {
    if (!this.state.failed) {
      return this.props.children;
    }
    return (
      <div className="alert alert-danger" role="alert">
        <p>No se pudo abrir esta página. Puede que el panel se haya actualizado mientras lo usabas.</p>
        <button type="button" className="button button-sm" onClick={() => window.location.reload()}>
          Recargar el panel
        </button>
      </div>
    );
  }
}
