import {
  ModelResponse,
  PolicyTypeResponse,
  CustomerResponse,
  AdminCustomerSummaryResponse,
  PageAdminCustomerSummaryResponse,
  CustomerCreatedResponse,
  TokenGeneratedResponse,
  TokensRevokedResponse,
  CustomerDisableResponse,
  SystemStatisticsResponse,
  CustomerConnectionStatusResponse,
  AuditLogResponse,
  PageAuditLogResponse,
  AuditStatisticsResponse,
  TokenStatisticsResponse,
  AllowancesByModelResponse,
  BulkUpdateResponse,
  UsageResetResponse,
  AuditCleanupResponse,
  CreateCustomerRequest,
  CreateModelRequest,
  UpdateModelRequest,
  CreatePolicyTypeRequest,
  UpdatePolicyTypeRequest,
  SetAllowanceRequest,
  BulkSetAllowanceRequest,
  BulkAllowanceUpdateRequest,
  GenerateTokenRequest,
  UpdateNotificationSettingsRequest,
  ModelAllowanceResponse,
  AgentConfigResponse,
  CustomerAgentTypesResponse,
  CustomerAgentTypeResponse,
  AgentTypeCustomersResponse,
  AgentTypeStatisticsResponse,
  UpdateAgentConfigRequest,
  AssignAgentTypeRequest,
  BulkAssignAgentTypesRequest,
  UpdateCustomerAgentTypeRequest,
  MonthlyTokenUsageResponse,
} from './types';

class ApiClient {
  private baseUrl: string;
  private token: string | null = null;

  constructor(baseUrl: string = '') {
    this.baseUrl = baseUrl;
  }

  setToken(token: string | null) {
    this.token = token;
    if (typeof window !== 'undefined') {
      if (token) {
        localStorage.setItem('admin_token', token);
      } else {
        localStorage.removeItem('admin_token');
      }
    }
  }

  getToken(): string | null {
    if (this.token) return this.token;
    if (typeof window !== 'undefined') {
      this.token = localStorage.getItem('admin_token');
    }
    return this.token;
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const token = this.getToken();
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    };

    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      ...options,
      headers,
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }));
      throw new Error(error.message || `HTTP ${response.status}: ${response.statusText}`);
    }

    // Handle empty responses
    const text = await response.text();
    if (!text) return {} as T;
    return JSON.parse(text);
  }

  // ============ Statistics ============
  async getSystemStatistics(): Promise<SystemStatisticsResponse> {
    return this.request('/api/admin/stats');
  }

  async getCustomerConnectionStatus(customerId: string): Promise<CustomerConnectionStatusResponse> {
    return this.request(`/api/admin/stats/customer/${customerId}/connection`);
  }

  // ============ Models ============
  async listModels(): Promise<ModelResponse[]> {
    return this.request('/api/admin/models');
  }

  async getModel(model: string): Promise<ModelResponse> {
    return this.request(`/api/models/${encodeURIComponent(model)}`);
  }

  async createModel(data: CreateModelRequest): Promise<ModelResponse> {
    return this.request('/api/models', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateModel(model: string, data: UpdateModelRequest): Promise<ModelResponse> {
    return this.request(`/api/admin/models/${encodeURIComponent(model)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async enableModel(model: string): Promise<ModelResponse> {
    return this.request(`/api/admin/models/${encodeURIComponent(model)}/enable`, {
      method: 'POST',
    });
  }

  async disableModel(model: string): Promise<ModelResponse> {
    return this.request(`/api/admin/models/${encodeURIComponent(model)}`, {
      method: 'DELETE',
    });
  }

  async linkModelToAllCustomers(model: string): Promise<void> {
    return this.request(`/api/models/${encodeURIComponent(model)}/link-all-customers`, {
      method: 'POST',
    });
  }

  // ============ Policy Types ============
  async listPolicyTypes(): Promise<PolicyTypeResponse[]> {
    return this.request('/api/admin/policy-types');
  }

  async createPolicyType(data: CreatePolicyTypeRequest): Promise<PolicyTypeResponse> {
    return this.request('/api/admin/policy-types', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updatePolicyType(id: string, data: UpdatePolicyTypeRequest): Promise<PolicyTypeResponse> {
    return this.request(`/api/admin/policy-types/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async disablePolicyType(id: string): Promise<PolicyTypeResponse> {
    return this.request(`/api/admin/policy-types/${id}`, {
      method: 'DELETE',
    });
  }

  // ============ Customers ============
  async listCustomers(page = 0, size = 20, enabled?: boolean): Promise<PageAdminCustomerSummaryResponse> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (enabled !== undefined) params.append('enabled', String(enabled));
    return this.request(`/api/admin/customers?${params}`);
  }

  async getCustomer(customerId: string): Promise<CustomerResponse> {
    return this.request(`/api/admin/customers/${customerId}`);
  }

  async createCustomer(data: CreateCustomerRequest): Promise<CustomerCreatedResponse> {
    return this.request('/api/customers', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async enableCustomer(customerId: string): Promise<void> {
    return this.request(`/api/admin/customers/${customerId}/enable`, {
      method: 'POST',
    });
  }

  async disableCustomer(customerId: string): Promise<CustomerDisableResponse> {
    return this.request(`/api/admin/customers/${customerId}`, {
      method: 'DELETE',
    });
  }

  async enableCustomerAllowances(customerId: string): Promise<void> {
    return this.request(`/api/admin/customers/${customerId}/enable-allowances`, {
      method: 'POST',
    });
  }

  async updateCustomerStatus(customerId: string, enabled: boolean): Promise<CustomerResponse> {
    return this.request(`/api/customers/${customerId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled }),
    });
  }

  async updateNotificationSettings(
    customerId: string,
    data: UpdateNotificationSettingsRequest
  ): Promise<void> {
    return this.request(`/api/customers/${customerId}/notification-settings`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    });
  }

  // ============ Customer Tokens ============
  async generateToken(customerId: string, data?: GenerateTokenRequest): Promise<TokenGeneratedResponse> {
    return this.request(`/api/customers/${customerId}/tokens`, {
      method: 'POST',
      body: JSON.stringify(data || {}),
    });
  }

  async revokeAllTokens(customerId: string): Promise<TokensRevokedResponse> {
    return this.request(`/api/customers/${customerId}/tokens`, {
      method: 'DELETE',
    });
  }

  async getTokenStatistics(): Promise<TokenStatisticsResponse[]> {
    return this.request('/api/admin/customers/tokens/stats');
  }

  // ============ Token Usage ============
  async getCustomerTokenUsage(
    customerId: string,
    months?: number,
    model?: string
  ): Promise<MonthlyTokenUsageResponse[]> {
    const params = new URLSearchParams();
    if (months !== undefined) params.append('months', String(months));
    if (model) params.append('model', model);
    const query = params.toString();
    return this.request(`/api/customers/${customerId}/token-usage${query ? `?${query}` : ''}`);
  }

  // ============ Allowances ============
  async getCustomerAllowances(customerId: string): Promise<ModelAllowanceResponse[]> {
    return this.request(`/api/customers/${customerId}/allowances`);
  }

  async setAllowance(
    customerId: string,
    model: string,
    data: SetAllowanceRequest
  ): Promise<ModelAllowanceResponse> {
    return this.request(`/api/customers/${customerId}/allowances/${encodeURIComponent(model)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async setBulkAllowance(customerId: string, data: BulkSetAllowanceRequest): Promise<void> {
    return this.request(`/api/customers/${customerId}/allowances`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async resetModelUsage(customerId: string, model: string): Promise<void> {
    return this.request(`/api/customers/${customerId}/allowances/${encodeURIComponent(model)}/reset`, {
      method: 'POST',
    });
  }

  async resetAllUsage(customerId: string): Promise<void> {
    return this.request(`/api/customers/${customerId}/allowances/reset-all`, {
      method: 'POST',
    });
  }

  async getAllowancesByModel(model: string): Promise<AllowancesByModelResponse> {
    return this.request(`/api/admin/allowances/by-model/${encodeURIComponent(model)}`);
  }

  async bulkUpdateAllowances(data: BulkAllowanceUpdateRequest): Promise<BulkUpdateResponse> {
    return this.request('/api/admin/allowances/bulk', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async resetAllUsageForModel(model: string): Promise<UsageResetResponse> {
    return this.request(`/api/admin/allowances/reset-all/${encodeURIComponent(model)}`, {
      method: 'POST',
    });
  }

  // ============ Audit Logs ============
  async queryAuditLogs(params: {
    customerId?: string;
    eventType?: string;
    eventCategory?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }): Promise<PageAuditLogResponse> {
    const searchParams = new URLSearchParams();
    if (params.customerId) searchParams.append('customerId', params.customerId);
    if (params.eventType) searchParams.append('eventType', params.eventType);
    if (params.eventCategory) searchParams.append('eventCategory', params.eventCategory);
    if (params.from) searchParams.append('from', params.from);
    if (params.to) searchParams.append('to', params.to);
    if (params.page !== undefined) searchParams.append('page', String(params.page));
    if (params.size !== undefined) searchParams.append('size', String(params.size));
    return this.request(`/api/admin/audit?${searchParams}`);
  }

  async getAuditStatistics(since?: string): Promise<AuditStatisticsResponse> {
    const params = since ? `?since=${encodeURIComponent(since)}` : '';
    return this.request(`/api/admin/audit/stats${params}`);
  }

  async cleanupAuditLogs(before: string): Promise<AuditCleanupResponse> {
    return this.request('/api/admin/audit/cleanup', {
      method: 'DELETE',
      body: JSON.stringify({ before }),
    });
  }

  // ============ Agent Types ============
  async listAgentTypes(): Promise<AgentConfigResponse[]> {
    return this.request('/api/admin/agent-types');
  }

  async getAgentType(agentType: string): Promise<AgentConfigResponse> {
    return this.request(`/api/admin/agent-types/${encodeURIComponent(agentType)}`);
  }

  async updateAgentType(agentType: string, data: UpdateAgentConfigRequest): Promise<AgentConfigResponse> {
    return this.request(`/api/admin/agent-types/${encodeURIComponent(agentType)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async enableAgentType(agentType: string): Promise<AgentConfigResponse> {
    return this.request(`/api/admin/agent-types/${encodeURIComponent(agentType)}/enable`, {
      method: 'POST',
    });
  }

  async disableAgentType(agentType: string): Promise<AgentConfigResponse> {
    return this.request(`/api/admin/agent-types/${encodeURIComponent(agentType)}`, {
      method: 'DELETE',
    });
  }

  async getAgentTypeStatistics(): Promise<AgentTypeStatisticsResponse> {
    return this.request('/api/admin/agent-types/stats');
  }

  async getCustomersByAgentType(agentType: string): Promise<AgentTypeCustomersResponse> {
    return this.request(`/api/admin/agent-types/${encodeURIComponent(agentType)}/customers`);
  }

  // ============ Customer Agent Types ============
  async getCustomerAgentTypes(customerId: string): Promise<CustomerAgentTypesResponse> {
    return this.request(`/api/admin/customers/${customerId}/agent-types`);
  }

  async assignAgentTypeToCustomer(
    customerId: string,
    data: AssignAgentTypeRequest
  ): Promise<CustomerAgentTypeResponse> {
    return this.request(`/api/admin/customers/${customerId}/agent-types`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async bulkAssignAgentTypes(
    customerId: string,
    data: BulkAssignAgentTypesRequest
  ): Promise<CustomerAgentTypesResponse> {
    return this.request(`/api/admin/customers/${customerId}/agent-types/bulk`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async updateCustomerAgentType(
    customerId: string,
    agentType: string,
    data: UpdateCustomerAgentTypeRequest
  ): Promise<CustomerAgentTypeResponse> {
    return this.request(`/api/admin/customers/${customerId}/agent-types/${encodeURIComponent(agentType)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async removeAgentTypeFromCustomer(customerId: string, agentType: string): Promise<void> {
    return this.request(`/api/admin/customers/${customerId}/agent-types/${encodeURIComponent(agentType)}`, {
      method: 'DELETE',
    });
  }
}

export const api = new ApiClient();
