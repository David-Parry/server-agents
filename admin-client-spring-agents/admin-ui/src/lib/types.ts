// API Response Types based on OpenAPI spec

export interface ModelResponse {
  model: string;
  provider: string;
  displayName?: string;
  description?: string;
  defaultTokensForNewCustomers?: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PolicyTypeResponse {
  id: string;
  name: string;
  description?: string;
  resetDays?: number;
  defaultTokens?: number;
  enabled: boolean;
}

export interface ModelAllowanceResponse {
  id: string;
  model: string;
  modelDisplayName?: string;
  provider: string;
  allowedTokens: number;
  tokensUsed: number;
  remainingTokens: number;
  unlimited: boolean;
  daysUntilReset?: number;
  tokensResetAt?: string;
  policyTypeName: string;
  resetDays?: number;
  minTokenNotificationThreshold?: number;
  effectiveNotificationThreshold?: number;
  belowNotificationThreshold: boolean;
  lowTokenNotificationSentAt?: string;
}

export interface CustomerResponse {
  id: string;
  customerId: string;
  name: string;
  enabled: boolean;
  policyTypeName: string;
  resetDays?: number;
  unlimitedPolicy: boolean;
  createdAt: string;
  updatedAt: string;
  modelAllowances: ModelAllowanceResponse[];
  defaultMinTokenNotificationThreshold?: number;
  notificationWebhookUrl?: string;
  notificationEmail?: string;
}

export interface AdminCustomerSummaryResponse {
  id: string;
  customerId: string;
  name: string;
  enabled: boolean;
  activeTokenCount: number;
  modelAllowanceCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface PageAdminCustomerSummaryResponse {
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  size: number;
  content: AdminCustomerSummaryResponse[];
  number: number;
  numberOfElements: number;
  empty: boolean;
}

export interface CustomerCreatedResponse {
  id: string;
  customerId: string;
  name: string;
  policyTypeName: string;
  resetDays?: number;
  unlimited: boolean;
  defaultAllowance?: number;
  apiToken: string;
  warning?: string;
}

export interface TokenGeneratedResponse {
  apiToken: string;
  expiresAt?: string;
  message: string;
}

export interface TokensRevokedResponse {
  revokedCount: number;
  message: string;
}

export interface CustomerDisableResponse {
  customerId: string;
  disabled: boolean;
  tokensRevoked: number;
  allowancesDisabled: number;
}

export interface SystemStatisticsResponse {
  totalCustomers: number;
  enabledCustomers: number;
  totalModels: number;
  enabledModels: number;
  totalPolicyTypes: number;
  totalAllowances: number;
  totalAuditLogs: number;
}

export interface ConnectionDetail {
  connectionId: string;
  connectedAt: string;
  lastActivityAt: string;
  activeSessions: number;
  totalSessionsCreated: number;
  totalToolCalls: number;
  connectionDurationMs: number;
  idleTimeMs: number;
}

export interface CustomerConnectionStatusResponse {
  customerId: string;
  customerName: string;
  connected: boolean;
  activeConnectionCount: number;
  totalActiveSessions: number;
  connections: ConnectionDetail[];
}

export interface AuditLogResponse {
  id: string;
  eventType: string;
  eventCategory: string;
  customerId?: string;
  tokenId?: string;
  secretVersion?: string;
  description: string;
  metadata?: string;
  actorType: string;
  actorId?: string;
  createdAt: string;
}

export interface PageAuditLogResponse {
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  size: number;
  content: AuditLogResponse[];
  number: number;
  numberOfElements: number;
  empty: boolean;
}

export interface AuditStatisticsResponse {
  eventCountsByType: Record<string, number>;
  totalEvents: number;
  since: string;
}

export interface TokenStatisticsResponse {
  secretVersion: string;
  activeTokenCount: number;
}

export interface AllowancesByModelResponse {
  model: string;
  modelDisplayName?: string;
  totalAllowances: number;
  allowances: CustomerAllowanceSummary[];
}

export interface CustomerAllowanceSummary {
  customerId: string;
  customerName: string;
  customerEnabled: boolean;
  allowedTokens: number;
  tokensUsed: number;
  remainingTokens: number;
  unlimited: boolean;
  policyTypeName: string;
  allowanceEnabled: boolean;
}

export interface BulkUpdateResponse {
  model: string;
  customersUpdated: number;
  allowedTokens?: number;
  policyTypeName?: string;
}

export interface UsageResetResponse {
  model: string;
  allowancesReset: number;
}

export interface AuditCleanupResponse {
  deletedCount: number;
  cutoffDate: string;
}

// Request Types
export interface CreateCustomerRequest {
  name: string;
  policyTypeName?: string;
  defaultAllowance?: number;
  unlimited?: boolean;
}

export interface CreateModelRequest {
  model: string;
  provider: string;
  displayName?: string;
  description?: string;
  defaultTokensForNewCustomers?: number;
}

export interface UpdateModelRequest {
  displayName?: string;
  description?: string;
  defaultTokensForNewCustomers?: number;
  enabled?: boolean;
}

export interface CreatePolicyTypeRequest {
  name: string;
  description?: string;
  resetDays?: number;
}

export interface UpdatePolicyTypeRequest {
  description?: string;
  resetDays?: number;
  enabled?: boolean;
}

export interface SetAllowanceRequest {
  allowedTokens?: number;
  policyTypeName?: string;
}

export interface BulkSetAllowanceRequest {
  allowedTokens?: number;
  unlimited?: boolean;
}

export interface BulkAllowanceUpdateRequest {
  model: string;
  allowedTokens?: number;
  policyTypeName?: string;
}

export interface GenerateTokenRequest {
  expiresInDays?: number;
  revokeExisting?: boolean;
}

export interface UpdateNotificationSettingsRequest {
  defaultNotificationThreshold?: number;
  webhookUrl?: string;
  email?: string;
}

export interface UpdateAllowanceNotificationThresholdRequest {
  threshold?: number;
}

export interface AuditCleanupRequest {
  before: string;
}

// Agent Types
export interface ExecutionConfigResponse {
  maxTokens?: number;
  temperature?: number;
  timeoutSeconds?: number;
  retryAttempts?: number;
  retryDelayMs?: number;
}

export interface AgentConfigResponse {
  id: string;
  agentType: string;
  name: string;
  description?: string;
  model: string;
  modelDisplayName?: string;
  enabled: boolean;
  version: number;
  executionConfig?: ExecutionConfigResponse;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerAgentTypeResponse {
  id: string;
  customerId: string;
  customerName: string;
  agentType: string;
  agentName: string;
  enabled: boolean;
  customTokenLimit?: number;
  priority?: number;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerAgentTypesResponse {
  customerId: string;
  customerName: string;
  totalAssigned: number;
  enabledCount: number;
  agentTypes: CustomerAgentTypeResponse[];
}

export interface AgentTypeCustomersResponse {
  agentType: string;
  agentName: string;
  totalCustomers: number;
  enabledCount: number;
  customers: CustomerAgentTypeResponse[];
}

export interface AgentTypeStatisticsResponse {
  customerCountByAgentType: Record<string, number>;
  totalAssignments: number;
  totalAgentTypes: number;
}

// Agent Type Request Types
export interface UpdateAgentConfigRequest {
  name?: string;
  description?: string;
  systemPrompt?: string;
  model?: string;
  enabled?: boolean;
  maxTokens?: number;
  temperature?: number;
  timeoutSeconds?: number;
  retryAttempts?: number;
  retryDelayMs?: number;
}

export interface AssignAgentTypeRequest {
  agentType: string;
  customTokenLimit?: number;
  priority?: number;
}

export interface BulkAssignAgentTypesRequest {
  agentTypes: string[];
  defaultCustomTokenLimit?: number;
  defaultPriority?: number;
}

export interface UpdateCustomerAgentTypeRequest {
  enabled?: boolean;
  customTokenLimit?: number;
  priority?: number;
}

// Token Usage
export interface MonthlyTokenUsageResponse {
  year: number;
  month: number;
  model: string;
  agentType: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  toolCallsCount: number;
  callCount: number;
  inputTokenPricePerMillion: number;
  outputTokenPricePerMillion: number;
  estimatedInputCost: number;
  estimatedOutputCost: number;
  estimatedTotalCost: number;
}
