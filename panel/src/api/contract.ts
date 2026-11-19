// Checks, when the panel is compiled, that the types written by hand in src/api/ still match the operator API as
// described in docs/openapi.json (generated into ./openapi.ts by `npm run api:types`). A renamed or removed field, a
// changed type, a new value of an enum the panel does not know or a field that may be null where the panel expects a
// value makes `tsc` fail on the line of that type.

import type { ActionParameter, ActionRun, AssistantAction, AssistantActionInput, Integration } from './actions';
import type { IntegrationActionDefinition, IntegrationInput, SigningSecret } from './actions';
import type { BillingOverview, Invoice, PlanChange, PlanView, PortalSession, Quota, TopUpPurchase } from './billing';
import type { TopUpView, UsageDay, UsageLine, UsageReport } from './billing';
import type {
  EmailChannel,
  EmailChannelInput,
  MetaChannel,
  MetaChannelInput,
  WhatsAppChannel,
  WhatsAppChannelInput,
} from './channels';
import type {
  AttentionAlert,
  AttentionSettings,
  Citation,
  ConversationDetail,
  ConversationRating,
  ConversationSummary,
  Customer,
  CustomerInput,
  CustomerListItem,
  DifficultQuestion,
  DraftAnswer,
  ModelChoice,
  ModelQuality,
  ModelUsage,
  OperatorMessage,
  OutcomeDay,
  OutcomeReport,
  PlaygroundQuestion,
  RatingComment,
} from './conversations';
import type { FollowUp, FollowUpInput, FollowUpTemplate, FollowUpTemplateInput, WhatsAppTemplate } from './follow-ups';
import type {
  InvitationPreview,
  IssuedInvitation,
  Invitation,
  Member,
  OperatorProfile,
  RegisterInput,
  SessionResponse,
} from './identity';
import type { DocumentSuggestion, KnowledgeDocument, KnowledgeGap, KnowledgeMatch, TaughtAnswer } from './knowledge';
import type { WebPageInput } from './knowledge';
import type { components } from './openapi';
import type { Page } from './page';
import type { PlatformAccess, PlatformPlan, PlatformWorkspace, PlatformWorkspacePage } from './platform';
import type { WidgetSettings, Workspace, WorkspaceMembership, WorkspaceSuspension } from './workspaces';

type Api = components['schemas'];

/**
 * A type with every field present and no nulls, all the way down: a request is compared on what it holds, since the
 * API takes a missing field as null.
 */
type Solid<T> = T extends readonly (infer E)[]
  ? Solid<E>[]
  : T extends object
    ? { [K in keyof T]-?: Solid<NonNullable<T[K]>> }
    : T;

/**
 * What the panel expects of an answer: every field it declares, present (the API writes every field of a response,
 * nulls included), and null only where the panel handles null. An object whose fields are all optional, such as a
 * count per usage metric, keeps them optional: the API describes it as a map and sends only the keys it has.
 */
type Expected<T> = T extends readonly (infer E)[]
  ? Expected<E>[]
  : T extends object
    ? Record<never, never> extends T
      ? { [K in keyof T]?: Expected<T[K]> }
      : { [K in keyof T]-?: Expected<Exclude<T[K], undefined>> }
    : T;

/**
 * What the API answers holds every field the panel reads, with a type the panel handles: a field the API may send
 * as null (`@Nullable` in the backend) must be one the panel declares `| null`.
 */
type Reads<Panel, Answer> = [Answer] extends [Expected<Panel>] ? true : false;

/** Every field the panel sends is one the API takes, with a type it accepts. */
type Sends<Panel, Request> = [keyof Panel] extends [keyof Request]
  ? [Solid<Panel>] extends [{ [K in keyof Panel & keyof Request]: Solid<NonNullable<Request[K]>> }]
    ? true
    : false
  : false;

type Holds<T extends true> = T;

/** Fails to compile when one of these is false; never used at run time. */
export type ApiContract = [
  // actions
  Holds<Reads<AssistantAction, Api['ActionView']>>,
  Holds<Sends<AssistantActionInput, Api['ActionRequest']>>,
  Holds<Reads<ActionParameter, Api['ActionParameter']>>,
  Holds<Reads<ActionRun, Api['ActionRunView']>>,
  Holds<Reads<Integration, Api['IntegrationView']>>,
  Holds<Reads<IntegrationActionDefinition, Api['ActionDefinition']>>,
  Holds<Sends<IntegrationInput, Api['IntegrationRequest']>>,
  Holds<Reads<SigningSecret, Api['SigningSecret']>>,
  // billing
  Holds<Reads<UsageReport, Api['UsageReport']>>,
  Holds<Reads<UsageLine, Api['UsageLine']>>,
  Holds<Reads<UsageDay, Api['UsageDay']>>,
  Holds<Reads<Quota, Api['Quota']>>,
  Holds<Reads<BillingOverview, Api['BillingOverview']>>,
  Holds<Reads<PlanView, Api['PlanView']>>,
  Holds<Reads<TopUpView, Api['TopUpView']>>,
  Holds<Reads<Invoice, Api['Invoice']>>,
  Holds<Reads<PlanChange, Api['PlanChange']>>,
  Holds<Reads<TopUpPurchase, Api['TopUpPurchase']>>,
  Holds<Reads<PortalSession, Api['PortalSession']>>,
  // channels
  Holds<Reads<WhatsAppChannel, Api['WhatsAppChannelView']>>,
  Holds<Sends<WhatsAppChannelInput, Api['WhatsAppChannelRequest']>>,
  Holds<Reads<EmailChannel, Api['EmailChannelView']>>,
  Holds<Sends<EmailChannelInput, Api['EmailChannelRequest']>>,
  Holds<Reads<MetaChannel, Api['MetaChannelView']>>,
  Holds<Sends<MetaChannelInput, Api['MetaChannelRequest']>>,
  // conversations
  Holds<Reads<ConversationSummary, Api['ConversationSummary']>>,
  Holds<Reads<Page<ConversationSummary>, Api['ConversationPageView']>>,
  Holds<Reads<ConversationDetail, Api['ConversationDetail']>>,
  Holds<Reads<OperatorMessage, Api['OperatorMessageView']>>,
  Holds<Reads<Citation, Api['Citation']>>,
  Holds<Reads<ModelUsage, Api['ModelUsage']>>,
  Holds<Reads<ConversationRating, Api['RatingView']>>,
  Holds<Reads<Customer, Api['CustomerView']>>,
  Holds<Sends<CustomerInput, Api['EditRequest']>>,
  Holds<Reads<CustomerListItem, Api['CustomerListItem']>>,
  Holds<Reads<Page<CustomerListItem>, Api['CustomerPageView']>>,
  Holds<Reads<AttentionSettings, Api['AttentionSettings']>>,
  Holds<Reads<AttentionAlert, Api['AlertView']>>,
  Holds<Reads<ModelChoice, Api['ModelChoiceView']>>,
  Holds<Sends<PlaygroundQuestion, Api['PlaygroundRequest']>>,
  Holds<Reads<OutcomeReport, Api['OutcomeReport']>>,
  Holds<Reads<OutcomeDay, Api['OutcomeDay']>>,
  Holds<Reads<RatingComment, Api['RatingComment']>>,
  Holds<Reads<ModelQuality, Api['ModelQuality']>>,
  Holds<Reads<DifficultQuestion, Api['DifficultQuestion']>>,
  Holds<Reads<Page<DifficultQuestion>, Api['DifficultQuestionPageView']>>,
  Holds<Reads<DraftAnswer, Api['DraftView']>>,
  // followups
  Holds<Reads<FollowUp, Api['FollowUpView']>>,
  Holds<Reads<Page<FollowUp>, Api['FollowUpPageView']>>,
  Holds<Sends<FollowUpInput, Api['ScheduleRequest']>>,
  Holds<Reads<FollowUpTemplate, Api['FollowUpTemplate']>>,
  Holds<Reads<WhatsAppTemplate, Api['WhatsAppTemplate']>>,
  Holds<Sends<FollowUpTemplateInput, Api['TemplateRequest']>>,
  // identity
  Holds<Reads<SessionResponse, Api['SessionResponse']>>,
  Holds<Reads<OperatorProfile, Api['OperatorProfile']>>,
  Holds<Reads<Member, Api['MemberView']>>,
  Holds<Reads<Invitation, Api['InvitationView']>>,
  Holds<Reads<IssuedInvitation, Api['IssuedInvitation']>>,
  Holds<Reads<InvitationPreview, Api['InvitationPreview']>>,
  Holds<Sends<RegisterInput, Api['RegisterRequest']>>,
  // knowledge
  Holds<Reads<KnowledgeDocument, Api['DocumentView']>>,
  Holds<Reads<Page<KnowledgeDocument>, Api['DocumentPageView']>>,
  Holds<Sends<WebPageInput, Api['WebPageRequest']>>,
  Holds<Reads<KnowledgeMatch, Api['KnowledgeMatch']>>,
  Holds<Reads<KnowledgeGap, Api['GapSummary']>>,
  Holds<Reads<Page<KnowledgeGap>, Api['GapPageView']>>,
  Holds<Reads<DocumentSuggestion, Api['DocumentSuggestion']>>,
  Holds<Reads<TaughtAnswer, Api['TaughtAnswerView']>>,
  // platform
  Holds<Reads<PlatformWorkspace, Api['PlatformWorkspace']>>,
  Holds<Reads<PlatformPlan, Api['WorkspacePlan']>>,
  Holds<Reads<PlatformAccess, Api['PlatformAccess']>>,
  Holds<Reads<PlatformWorkspacePage, Api['PlatformWorkspacePage']>>,
  // workspaces
  Holds<Reads<Workspace, Api['WorkspaceView']>>,
  Holds<Reads<WorkspaceSuspension, Api['WorkspaceSuspension']>>,
  Holds<Reads<WorkspaceMembership, Api['WorkspaceMembership']>>,
  Holds<Sends<WidgetSettings, Api['WidgetSettingsRequest']>>,
];
