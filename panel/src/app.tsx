import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router';
import { ApiError } from './api/http-client';
import { AuthProvider } from './auth/auth-context';
import { GuestOnly, RequireAuth } from './auth/route-guards';
import { Layout } from './components/layout';
import { PageErrorBoundary } from './components/page-error-boundary';
import { Spinner } from './components/ui';
import { LoginPage, RegisterPage } from './pages/auth-pages';
import { ConversationsPage } from './pages/conversations-page';

// Each page is downloaded the first time it is opened, so signing in does not download the whole panel. The
// conversations page, where everyone lands, and the sign-in pages come with the first download
const ActionsPage = lazy(() => import('./pages/actions-page').then((page) => ({ default: page.ActionsPage })));
const AttentionPage = lazy(() => import('./pages/attention-page').then((page) => ({ default: page.AttentionPage })));
const BillingPage = lazy(() => import('./pages/billing-page').then((page) => ({ default: page.BillingPage })));
const CustomersPage = lazy(() => import('./pages/customers-page').then((page) => ({ default: page.CustomersPage })));
const DifficultPage = lazy(() => import('./pages/difficult-page').then((page) => ({ default: page.DifficultPage })));
const EmailPage = lazy(() => import('./pages/email-page').then((page) => ({ default: page.EmailPage })));
const FollowUpsPage = lazy(() => import('./pages/follow-ups-page').then((page) => ({ default: page.FollowUpsPage })));
const GapsPage = lazy(() => import('./pages/gaps-page').then((page) => ({ default: page.GapsPage })));
const IntegrationsPage = lazy(() =>
  import('./pages/integrations-page').then((page) => ({ default: page.IntegrationsPage })),
);
const InvitationPage = lazy(() => import('./pages/invitation-page').then((page) => ({ default: page.InvitationPage })));
const KnowledgePage = lazy(() => import('./pages/knowledge-page').then((page) => ({ default: page.KnowledgePage })));
const MetaPage = lazy(() => import('./pages/meta-page').then((page) => ({ default: page.MetaPage })));
const OutcomesPage = lazy(() => import('./pages/outcomes-page').then((page) => ({ default: page.OutcomesPage })));
const PlatformPage = lazy(() => import('./pages/platform-page').then((page) => ({ default: page.PlatformPage })));
const PlaygroundPage = lazy(() => import('./pages/playground-page').then((page) => ({ default: page.PlaygroundPage })));
const SearchPage = lazy(() => import('./pages/search-page').then((page) => ({ default: page.SearchPage })));
const TeamPage = lazy(() => import('./pages/team-page').then((page) => ({ default: page.TeamPage })));
const UsagePage = lazy(() => import('./pages/usage-page').then((page) => ({ default: page.UsagePage })));
const WhatsAppPage = lazy(() => import('./pages/whatsapp-page').then((page) => ({ default: page.WhatsAppPage })));
const WidgetPage = lazy(() => import('./pages/widget-page').then((page) => ({ default: page.WidgetPage })));

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 10_000,
        // A client error (not found, forbidden, invalid) will not fix itself by asking again
        retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 2,
      },
    },
  });
}

/** Routes and providers; the router itself is supplied by the caller (browser, or memory in tests). */
export function App({ queryClient }: { queryClient: QueryClient }) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </QueryClientProvider>
  );
}

function AppRoutes() {
  // Pages outside the layout, such as the invitation, load here
  const { pathname } = useLocation();
  return (
    <PageErrorBoundary resetKey={pathname}>
      <Suspense fallback={<Spinner />}>
        <Routes>
          <Route element={<GuestOnly />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>
          <Route element={<RequireAuth />}>
            <Route element={<Layout />}>
              <Route path="/conversations" element={<ConversationsPage />} />
              <Route path="/conversations/:conversationId" element={<ConversationsPage />} />
              <Route path="/customers" element={<CustomersPage />} />
              <Route path="/follow-ups" element={<FollowUpsPage />} />
              <Route path="/attention" element={<AttentionPage />} />
              <Route path="/playground" element={<PlaygroundPage />} />
              <Route path="/knowledge" element={<KnowledgePage />} />
              <Route path="/knowledge/search" element={<SearchPage />} />
              <Route path="/knowledge/gaps" element={<GapsPage />} />
              <Route path="/knowledge/difficult" element={<DifficultPage />} />
              <Route path="/widget" element={<WidgetPage />} />
              <Route path="/team" element={<TeamPage />} />
              <Route path="/actions" element={<ActionsPage />} />
              <Route path="/actions/integrations" element={<IntegrationsPage />} />
              <Route path="/channels/whatsapp" element={<WhatsAppPage />} />
              <Route path="/channels/email" element={<EmailPage />} />
              <Route path="/channels/messenger" element={<MetaPage key="messenger" platform="MESSENGER" />} />
              <Route path="/channels/instagram" element={<MetaPage key="instagram" platform="INSTAGRAM" />} />
              <Route path="/outcomes" element={<OutcomesPage />} />
              <Route path="/usage" element={<UsagePage />} />
              <Route path="/billing" element={<BillingPage />} />
              <Route path="/platform" element={<PlatformPage />} />
            </Route>
          </Route>
          {/* Signed in or not: accepting may sign in someone new */}
          <Route path="/invitations/accept" element={<InvitationPage />} />
          <Route path="*" element={<Navigate to="/conversations" replace />} />
        </Routes>
      </Suspense>
    </PageErrorBoundary>
  );
}
