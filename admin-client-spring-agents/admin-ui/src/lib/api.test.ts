import { describe, expect, it, beforeEach, vi } from 'vitest';
import { ApiClient } from './api';

describe('ApiClient', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('persists token and sends bearer header', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => '{"ok":true}',
    });
    vi.stubGlobal('fetch', fetchMock);

    const client = new ApiClient('');
    client.setToken('secret-token');
    await client.getSystemStatistics();

    expect(localStorage.getItem('admin_token')).toBe('secret-token');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/stats',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer secret-token',
        }),
      })
    );
  });

  it('throws backend message when request fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        json: async () => ({ message: 'Invalid admin token' }),
      })
    );

    const client = new ApiClient('');
    await expect(client.getSystemStatistics()).rejects.toThrow('Invalid admin token');
  });

  it('uses expected endpoints for model and customer workflows', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => '{}',
    });
    vi.stubGlobal('fetch', fetchMock);

    const client = new ApiClient('');
    await client.listModels();
    await client.getModel('claude-sonnet-4-5');
    await client.createModel({} as any);
    await client.updateModel('claude-sonnet-4-5', {} as any);
    await client.enableModel('claude-sonnet-4-5');
    await client.disableModel('claude-sonnet-4-5');
    await client.linkModelToAllCustomers('claude-sonnet-4-5');
    await client.listCustomers(1, 25, true);
    await client.getCustomer('cust-1');
    await client.createCustomer({} as any);
    await client.enableCustomer('cust-1');
    await client.disableCustomer('cust-1');
    await client.updateCustomerStatus('cust-1', true);
    await client.updateNotificationSettings('cust-1', {} as any);
    await client.generateToken('cust-1', {} as any);
    await client.revokeAllTokens('cust-1');

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/models', expect.anything());
    expect(fetchMock).toHaveBeenCalledWith('/api/models/claude-sonnet-4-5', expect.anything());
    expect(fetchMock).toHaveBeenCalledWith('/api/customers', expect.objectContaining({ method: 'POST' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/customers/cust-1/enable', expect.objectContaining({ method: 'POST' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/customers/cust-1/tokens', expect.objectContaining({ method: 'POST' }));
  });

  it('uses expected endpoints for policies, allowances, audit and agent types', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => '{}',
    });
    vi.stubGlobal('fetch', fetchMock);

    const client = new ApiClient('');
    await client.listPolicyTypes();
    await client.createPolicyType({} as any);
    await client.updatePolicyType('p1', {} as any);
    await client.disablePolicyType('p1');
    await client.getCustomerAllowances('cust-1');
    await client.setAllowance('cust-1', 'model-a', {} as any);
    await client.setBulkAllowance('cust-1', {} as any);
    await client.resetModelUsage('cust-1', 'model-a');
    await client.resetAllUsage('cust-1');
    await client.getAllowancesByModel('model-a');
    await client.bulkUpdateAllowances({} as any);
    await client.resetAllUsageForModel('model-a');
    await client.queryAuditLogs({ page: 0, size: 10, customerId: 'cust-1', eventType: 'LOGIN' });
    await client.getAuditStatistics('2026-01-01T00:00:00Z');
    await client.cleanupAuditLogs('2026-01-01T00:00:00Z');
    await client.listAgentTypes();
    await client.getAgentType('REVIEWER');
    await client.updateAgentType('REVIEWER', {} as any);
    await client.enableAgentType('REVIEWER');
    await client.disableAgentType('REVIEWER');
    await client.getAgentTypeStatistics();
    await client.getCustomersByAgentType('REVIEWER');
    await client.getCustomerAgentTypes('cust-1');
    await client.assignAgentTypeToCustomer('cust-1', {} as any);
    await client.bulkAssignAgentTypes('cust-1', {} as any);
    await client.updateCustomerAgentType('cust-1', 'REVIEWER', {} as any);
    await client.removeAgentTypeFromCustomer('cust-1', 'REVIEWER');

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/policy-types', expect.anything());
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/allowances/bulk', expect.objectContaining({ method: 'PUT' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/audit/cleanup', expect.objectContaining({ method: 'DELETE' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/agent-types/REVIEWER/enable', expect.objectContaining({ method: 'POST' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/customers/cust-1/agent-types/REVIEWER', expect.objectContaining({ method: 'DELETE' }));
  });
});
