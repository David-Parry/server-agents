'use client';

import { useState, useMemo } from 'react';
import { useParams } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import {
  Card,
  CardContent,
  CardHeader,
  Button,
  Input,
  Modal,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  Badge,
  Spinner,
  Toggle,
} from '@/components/ui';
import {
  ArrowLeft,
  Key,
  Trash2,
  RefreshCw,
  Copy,
  Check,
  Wifi,
  WifiOff,
  Bell,
  Activity,
  Bot,
  Plus,
  Settings,
  X,
  BarChart3,
  ChevronDown,
  ChevronRight,
} from 'lucide-react';
import { format } from 'date-fns';
import Link from 'next/link';
import { CustomerAgentTypeResponse, MonthlyTokenUsageResponse } from '@/lib/types';

export default function CustomerDetailPage() {
  const params = useParams();
  const queryClient = useQueryClient();
  const customerId = params.customerId as string;

  const [showTokenModal, setShowTokenModal] = useState(false);
  const [showNotificationModal, setShowNotificationModal] = useState(false);
  const [showAgentModal, setShowAgentModal] = useState(false);
  const [showEditAgentModal, setShowEditAgentModal] = useState(false);
  const [selectedAgentAssignment, setSelectedAgentAssignment] = useState<CustomerAgentTypeResponse | null>(null);
  const [generatedToken, setGeneratedToken] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [notificationSettings, setNotificationSettings] = useState({
    defaultNotificationThreshold: 0,
    webhookUrl: '',
    email: '',
  });
  const [newAgentAssignment, setNewAgentAssignment] = useState({
    agentType: '',
    customTokenLimit: '',
    priority: '',
  });
  const [editAgentSettings, setEditAgentSettings] = useState({
    enabled: true,
    customTokenLimit: '',
    priority: '',
  });
  const [tokenUsageModelFilter, setTokenUsageModelFilter] = useState<string>('');
  const [expandedMonths, setExpandedMonths] = useState<Set<string>>(new Set());

  const { data: customer, isLoading } = useQuery({
    queryKey: ['customer', customerId],
    queryFn: () => api.getCustomer(customerId),
  });

  const { data: connectionStatus } = useQuery({
    queryKey: ['customer-connection', customerId],
    queryFn: () => api.getCustomerConnectionStatus(customerId),
    refetchInterval: 10000, // Refresh every 10 seconds
  });

  const { data: customerAgentTypes, isLoading: isLoadingAgentTypes } = useQuery({
    queryKey: ['customer-agent-types', customerId],
    queryFn: () => api.getCustomerAgentTypes(customerId),
  });

  const { data: allAgentTypes } = useQuery({
    queryKey: ['agent-types'],
    queryFn: () => api.listAgentTypes(),
  });

  const { data: tokenUsageData, isLoading: isLoadingTokenUsage } = useQuery({
    queryKey: ['customer-token-usage', customerId, tokenUsageModelFilter],
    queryFn: () =>
      api.getCustomerTokenUsage(
        customerId,
        12,
        tokenUsageModelFilter || undefined
      ),
  });

  // Group token usage by month, with per-model and per-agent-type breakdown
  const tokenUsageByMonth = useMemo(() => {
    if (!tokenUsageData) return [];

    const monthMap = new Map<
      string,
      {
        year: number;
        month: number;
        key: string;
        totalTokens: number;
        promptTokens: number;
        completionTokens: number;
        totalCalls: number;
        totalToolCalls: number;
        rows: MonthlyTokenUsageResponse[];
      }
    >();

    for (const row of tokenUsageData) {
      const key = `${row.year}-${String(row.month).padStart(2, '0')}`;
      const existing = monthMap.get(key);
      if (existing) {
        existing.totalTokens += row.totalTokens;
        existing.promptTokens += row.promptTokens;
        existing.completionTokens += row.completionTokens;
        existing.totalCalls += row.callCount;
        existing.totalToolCalls += row.toolCallsCount;
        existing.rows.push(row);
      } else {
        monthMap.set(key, {
          year: row.year,
          month: row.month,
          key,
          totalTokens: row.totalTokens,
          promptTokens: row.promptTokens,
          completionTokens: row.completionTokens,
          totalCalls: row.callCount,
          totalToolCalls: row.toolCallsCount,
          rows: [row],
        });
      }
    }

    return Array.from(monthMap.values()).sort((a, b) =>
      b.key.localeCompare(a.key)
    );
  }, [tokenUsageData]);

  // Get unique models from usage data for the filter dropdown
  const availableModelsForFilter = useMemo(() => {
    if (!tokenUsageData) return [];
    const models = new Set(tokenUsageData.map((r) => r.model));
    return Array.from(models).sort();
  }, [tokenUsageData]);

  const currentMonth = useMemo(() => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }, []);

  const toggleMonth = (key: string) => {
    setExpandedMonths((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const monthName = (month: number) => {
    return new Date(2000, month - 1).toLocaleString('default', { month: 'long' });
  };

  const generateTokenMutation = useMutation({
    mutationFn: () => api.generateToken(customerId),
    onSuccess: (response) => {
      setGeneratedToken(response.apiToken);
      setShowTokenModal(true);
    },
  });

  const revokeTokensMutation = useMutation({
    mutationFn: () => api.revokeAllTokens(customerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] });
    },
  });

  const resetUsageMutation = useMutation({
    mutationFn: (model: string) => api.resetModelUsage(customerId, model),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] });
    },
  });

  const resetAllUsageMutation = useMutation({
    mutationFn: () => api.resetAllUsage(customerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] });
    },
  });

  const updateNotificationsMutation = useMutation({
    mutationFn: () => api.updateNotificationSettings(customerId, notificationSettings),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] });
      setShowNotificationModal(false);
    },
  });

  const enableMutation = useMutation({
    mutationFn: () => api.enableCustomer(customerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] });
    },
  });

  const disableMutation = useMutation({
    mutationFn: () => api.disableCustomer(customerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] });
    },
  });

  const assignAgentTypeMutation = useMutation({
    mutationFn: () =>
      api.assignAgentTypeToCustomer(customerId, {
        agentType: newAgentAssignment.agentType,
        customTokenLimit: newAgentAssignment.customTokenLimit
          ? parseInt(newAgentAssignment.customTokenLimit)
          : undefined,
        priority: newAgentAssignment.priority ? parseInt(newAgentAssignment.priority) : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer-agent-types', customerId] });
      setShowAgentModal(false);
      setNewAgentAssignment({ agentType: '', customTokenLimit: '', priority: '' });
    },
  });

  const updateAgentAssignmentMutation = useMutation({
    mutationFn: () =>
      api.updateCustomerAgentType(customerId, selectedAgentAssignment!.agentType, {
        enabled: editAgentSettings.enabled,
        customTokenLimit: editAgentSettings.customTokenLimit
          ? parseInt(editAgentSettings.customTokenLimit)
          : undefined,
        priority: editAgentSettings.priority ? parseInt(editAgentSettings.priority) : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer-agent-types', customerId] });
      setShowEditAgentModal(false);
      setSelectedAgentAssignment(null);
    },
  });

  const removeAgentTypeMutation = useMutation({
    mutationFn: (agentType: string) => api.removeAgentTypeFromCustomer(customerId, agentType),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer-agent-types', customerId] });
    },
  });

  const handleCopyToken = () => {
    if (generatedToken) {
      navigator.clipboard.writeText(generatedToken);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleEditAgentAssignment = (assignment: CustomerAgentTypeResponse) => {
    setSelectedAgentAssignment(assignment);
    setEditAgentSettings({
      enabled: assignment.enabled,
      customTokenLimit: assignment.customTokenLimit?.toString() || '',
      priority: assignment.priority?.toString() || '',
    });
    setShowEditAgentModal(true);
  };

  // Get available agent types (not already assigned)
  const availableAgentTypes =
    allAgentTypes?.filter(
      (at) =>
        at.enabled &&
        !customerAgentTypes?.agentTypes?.some((cat) => cat.agentType === at.agentType)
    ) || [];

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner className="w-8 h-8" />
      </div>
    );
  }

  if (!customer) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-400">Customer not found</p>
        <Link href="/customers">
          <Button variant="secondary" className="mt-4">
            Back to Customers
          </Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link href="/customers">
            <Button variant="ghost" size="sm">
              <ArrowLeft className="w-4 h-4" />
            </Button>
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-white">{customer.name}</h1>
            <p className="text-gray-400 font-mono text-sm">{customer.customerId}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant={customer.enabled ? 'success' : 'danger'}>
            {customer.enabled ? 'Enabled' : 'Disabled'}
          </Badge>
          {connectionStatus?.connected && (
            <Badge variant="info" className="flex items-center gap-1">
              <Wifi className="w-3 h-3" />
              Connected
            </Badge>
          )}
        </div>
      </div>

      {/* Quick Actions */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card>
          <CardContent className="py-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-400">Model Allowances</p>
                <p className="text-lg font-semibold text-white">
                  {customer.modelAllowances?.length ?? 0}
                </p>
              </div>
              <Activity className="w-6 h-6 text-blue-400" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="py-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-400">Agent Types</p>
                <p className="text-lg font-semibold text-white">
                  {customerAgentTypes?.enabledCount ?? 0} / {customerAgentTypes?.totalAssigned ?? 0}
                </p>
              </div>
              <Bot className="w-6 h-6 text-purple-400" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="py-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-400">Connection Status</p>
                <p className="text-lg font-semibold text-white">
                  {connectionStatus?.connected ? 'Online' : 'Offline'}
                </p>
              </div>
              {connectionStatus?.connected ? (
                <Wifi className="w-6 h-6 text-green-400" />
              ) : (
                <WifiOff className="w-6 h-6 text-gray-500" />
              )}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="py-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-400">Created</p>
                <p className="text-lg font-semibold text-white">
                  {format(new Date(customer.createdAt), 'MMM d, yyyy')}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Actions Row */}
      <Card>
        <CardContent className="py-4">
          <div className="flex flex-wrap items-center gap-3">
            <Button
              onClick={() => generateTokenMutation.mutate()}
              disabled={!customer.enabled}
              loading={generateTokenMutation.isPending}
            >
              <Key className="w-4 h-4 mr-2" />
              Generate Token
            </Button>
            <Button
              variant="secondary"
              onClick={() => revokeTokensMutation.mutate()}
              loading={revokeTokensMutation.isPending}
            >
              <Trash2 className="w-4 h-4 mr-2" />
              Revoke All Tokens
            </Button>
            <Button
              variant="secondary"
              onClick={() => resetAllUsageMutation.mutate()}
              loading={resetAllUsageMutation.isPending}
            >
              <RefreshCw className="w-4 h-4 mr-2" />
              Reset All Usage
            </Button>
            <Button
              variant="secondary"
              onClick={() => {
                setNotificationSettings({
                  defaultNotificationThreshold: customer.defaultMinTokenNotificationThreshold || 0,
                  webhookUrl: customer.notificationWebhookUrl || '',
                  email: customer.notificationEmail || '',
                });
                setShowNotificationModal(true);
              }}
            >
              <Bell className="w-4 h-4 mr-2" />
              Notifications
            </Button>
            <div className="flex-1" />
            {customer.enabled ? (
              <Button
                variant="danger"
                onClick={() => disableMutation.mutate()}
                loading={disableMutation.isPending}
              >
                Disable Customer
              </Button>
            ) : (
              <Button
                variant="primary"
                onClick={() => enableMutation.mutate()}
                loading={enableMutation.isPending}
              >
                Enable Customer
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Connection Details */}
      {connectionStatus?.connected && connectionStatus.connections.length > 0 && (
        <Card>
          <CardHeader>
            <h2 className="text-lg font-semibold text-white flex items-center gap-2">
              <Wifi className="w-5 h-5 text-green-400" />
              Active Connections ({connectionStatus.activeConnectionCount})
            </h2>
          </CardHeader>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Connection ID</TableHead>
                <TableHead>Connected At</TableHead>
                <TableHead>Active Sessions</TableHead>
                <TableHead>Total Tool Calls</TableHead>
                <TableHead>Duration</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {connectionStatus.connections.map((conn) => (
                <TableRow key={conn.connectionId}>
                  <TableCell className="font-mono text-xs">{conn.connectionId}</TableCell>
                  <TableCell>
                    {format(new Date(conn.connectedAt), 'MMM d, HH:mm:ss')}
                  </TableCell>
                  <TableCell>{conn.activeSessions}</TableCell>
                  <TableCell>{conn.totalToolCalls}</TableCell>
                  <TableCell>
                    {Math.round(conn.connectionDurationMs / 1000 / 60)} min
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      {/* Agent Types Configuration */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-white flex items-center gap-2">
              <Bot className="w-5 h-5 text-purple-400" />
              Agent Configuration
            </h2>
            <Button
              size="sm"
              onClick={() => setShowAgentModal(true)}
              disabled={availableAgentTypes.length === 0}
            >
              <Plus className="w-4 h-4 mr-2" />
              Assign Agent
            </Button>
          </div>
        </CardHeader>
        {isLoadingAgentTypes ? (
          <CardContent>
            <div className="flex items-center justify-center py-8">
              <Spinner className="w-6 h-6" />
            </div>
          </CardContent>
        ) : customerAgentTypes?.agentTypes && customerAgentTypes.agentTypes.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Agent Type</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Token Limit</TableHead>
                <TableHead>Priority</TableHead>
                <TableHead>Assigned</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {customerAgentTypes.agentTypes.map((assignment) => (
                <TableRow key={assignment.id}>
                  <TableCell>
                    <code className="px-2 py-1 bg-gray-800 rounded text-sm text-purple-400">
                      {assignment.agentType}
                    </code>
                  </TableCell>
                  <TableCell className="text-white">{assignment.agentName}</TableCell>
                  <TableCell>
                    <Badge variant={assignment.enabled ? 'success' : 'danger'}>
                      {assignment.enabled ? 'Enabled' : 'Disabled'}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {assignment.customTokenLimit ? (
                      <span className="text-white">
                        {assignment.customTokenLimit.toLocaleString()}
                      </span>
                    ) : (
                      <span className="text-gray-500">Default</span>
                    )}
                  </TableCell>
                  <TableCell>
                    {assignment.priority !== undefined && assignment.priority !== null ? (
                      <span className="text-white">{assignment.priority}</span>
                    ) : (
                      <span className="text-gray-500">—</span>
                    )}
                  </TableCell>
                  <TableCell className="text-gray-400 text-sm">
                    {format(new Date(assignment.createdAt), 'MMM d, yyyy')}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleEditAgentAssignment(assignment)}
                        title="Edit Assignment"
                      >
                        <Settings className="w-4 h-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => removeAgentTypeMutation.mutate(assignment.agentType)}
                        title="Remove Agent"
                        className="text-red-400 hover:text-red-300"
                      >
                        <X className="w-4 h-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <CardContent>
            <p className="text-gray-400 text-center py-4">
              No agent types assigned to this customer
            </p>
          </CardContent>
        )}
      </Card>

      {/* Token Usage */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-white flex items-center gap-2">
              <BarChart3 className="w-5 h-5 text-blue-400" />
              Token Usage
            </h2>
            {!tokenUsageModelFilter && availableModelsForFilter.length > 1 && (
              <select
                className="px-3 py-1.5 bg-gray-700 border border-gray-600 rounded-lg text-sm text-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={tokenUsageModelFilter}
                onChange={(e) => setTokenUsageModelFilter(e.target.value)}
              >
                <option value="">All Models</option>
                {availableModelsForFilter.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            )}
            {tokenUsageModelFilter && (
              <div className="flex items-center gap-2">
                <Badge variant="info">{tokenUsageModelFilter}</Badge>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setTokenUsageModelFilter('')}
                >
                  <X className="w-4 h-4" />
                </Button>
              </div>
            )}
            <Button
              variant="ghost"
              size="sm"
              onClick={() =>
                queryClient.invalidateQueries({
                  queryKey: ['customer-token-usage', customerId],
                })
              }
              title="Refresh token usage"
            >
              <RefreshCw className="w-4 h-4" />
            </Button>
          </div>
        </CardHeader>
        {isLoadingTokenUsage ? (
          <CardContent>
            <div className="flex items-center justify-center py-8">
              <Spinner className="w-6 h-6" />
            </div>
          </CardContent>
        ) : tokenUsageByMonth.length > 0 ? (
          <div>
            {/* Current month summary */}
            {tokenUsageByMonth[0] && (
              <CardContent className="border-b border-gray-700">
                <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                  <div>
                    <p className="text-xs text-gray-400 uppercase">Latest Month</p>
                    <p className="text-lg font-semibold text-white">
                      {monthName(tokenUsageByMonth[0].month)} {tokenUsageByMonth[0].year}
                    </p>
                    {tokenUsageByMonth[0].key === currentMonth && (
                      <Badge variant="info" className="mt-1">Current</Badge>
                    )}
                  </div>
                  <div>
                    <p className="text-xs text-gray-400 uppercase">Total Tokens</p>
                    <p className="text-lg font-semibold text-white">
                      {tokenUsageByMonth[0].totalTokens.toLocaleString()}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-400 uppercase">Prompt</p>
                    <p className="text-lg font-semibold text-blue-400">
                      {tokenUsageByMonth[0].promptTokens.toLocaleString()}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-400 uppercase">Completion</p>
                    <p className="text-lg font-semibold text-green-400">
                      {tokenUsageByMonth[0].completionTokens.toLocaleString()}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-400 uppercase">API Calls</p>
                    <p className="text-lg font-semibold text-white">
                      {tokenUsageByMonth[0].totalCalls.toLocaleString()}
                    </p>
                  </div>
                </div>
              </CardContent>
            )}

            {/* Monthly breakdown table */}
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead>Total Tokens</TableHead>
                  <TableHead>Prompt</TableHead>
                  <TableHead>Completion</TableHead>
                  <TableHead>Calls</TableHead>
                  <TableHead>Tool Calls</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tokenUsageByMonth.map((month) => (
                  <>
                    <TableRow
                      key={month.key}
                      className={month.key === currentMonth ? 'bg-blue-900/10' : ''}
                    >
                      <TableCell>
                        <button
                          className="flex items-center gap-1 text-white font-medium hover:text-blue-400 transition-colors"
                          onClick={() => toggleMonth(month.key)}
                        >
                          {expandedMonths.has(month.key) ? (
                            <ChevronDown className="w-4 h-4" />
                          ) : (
                            <ChevronRight className="w-4 h-4" />
                          )}
                          {monthName(month.month)} {month.year}
                          {month.key === currentMonth && (
                            <Badge variant="info" className="ml-2">Current</Badge>
                          )}
                        </button>
                      </TableCell>
                      <TableCell className="text-white font-medium">
                        {month.totalTokens.toLocaleString()}
                      </TableCell>
                      <TableCell>{month.promptTokens.toLocaleString()}</TableCell>
                      <TableCell>{month.completionTokens.toLocaleString()}</TableCell>
                      <TableCell>{month.totalCalls.toLocaleString()}</TableCell>
                      <TableCell>{month.totalToolCalls.toLocaleString()}</TableCell>
                    </TableRow>
                    {expandedMonths.has(month.key) &&
                      month.rows.map((row, idx) => (
                        <TableRow key={`${month.key}-${row.model}-${row.agentType}-${idx}`}>
                          <TableCell className="pl-10">
                            <div className="flex items-center gap-2">
                              <code className="px-1.5 py-0.5 bg-gray-800 rounded text-xs text-blue-400">
                                {row.model}
                              </code>
                              {row.agentType && (
                                <code className="px-1.5 py-0.5 bg-gray-800 rounded text-xs text-purple-400">
                                  {row.agentType}
                                </code>
                              )}
                            </div>
                          </TableCell>
                          <TableCell>{row.totalTokens.toLocaleString()}</TableCell>
                          <TableCell>{row.promptTokens.toLocaleString()}</TableCell>
                          <TableCell>{row.completionTokens.toLocaleString()}</TableCell>
                          <TableCell>{row.callCount.toLocaleString()}</TableCell>
                          <TableCell>{row.toolCallsCount.toLocaleString()}</TableCell>
                        </TableRow>
                      ))}
                  </>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : (
          <CardContent>
            <p className="text-gray-400 text-center py-4">No token usage data available</p>
          </CardContent>
        )}
      </Card>

      {/* Model Allowances */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white">Model Allowances</h2>
        </CardHeader>
        {customer.modelAllowances && customer.modelAllowances.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Model</TableHead>
                <TableHead>Provider</TableHead>
                <TableHead>Policy</TableHead>
                <TableHead>Usage</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {customer.modelAllowances.map((allowance) => (
                <TableRow key={allowance.id}>
                  <TableCell>
                    <div>
                      <p className="font-medium text-white">
                        {allowance.modelDisplayName || allowance.model}
                      </p>
                      <p className="text-xs text-gray-500">{allowance.model}</p>
                    </div>
                  </TableCell>
                  <TableCell>{allowance.provider}</TableCell>
                  <TableCell>
                    <Badge variant={allowance.unlimited ? 'success' : 'info'}>
                      {allowance.policyTypeName}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {allowance.unlimited ? (
                      <span className="text-gray-400">Unlimited</span>
                    ) : (
                      <div>
                        <p className="text-white">
                          {allowance.tokensUsed.toLocaleString()} /{' '}
                          {allowance.allowedTokens.toLocaleString()}
                        </p>
                        <div className="w-24 h-1.5 bg-gray-700 rounded-full mt-1">
                          <div
                            className="h-full bg-blue-500 rounded-full"
                            style={{
                              width: `${Math.min(
                                (allowance.tokensUsed / allowance.allowedTokens) * 100,
                                100
                              )}%`,
                            }}
                          />
                        </div>
                      </div>
                    )}
                  </TableCell>
                  <TableCell>
                    {allowance.belowNotificationThreshold ? (
                      <Badge variant="warning">Low Tokens</Badge>
                    ) : (
                      <Badge variant="success">OK</Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => resetUsageMutation.mutate(allowance.model)}
                      title="Reset Usage"
                    >
                      <RefreshCw className="w-4 h-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <CardContent>
            <p className="text-gray-400 text-center py-4">No model allowances configured</p>
          </CardContent>
        )}
      </Card>

      {/* Token Modal */}
      <Modal
        isOpen={showTokenModal}
        onClose={() => {
          setShowTokenModal(false);
          setGeneratedToken(null);
        }}
        title="API Token Generated"
        size="md"
      >
        <div className="space-y-4">
          <div className="p-4 bg-yellow-900/20 border border-yellow-700 rounded-lg">
            <p className="text-sm text-yellow-400">
              ⚠️ Make sure to copy this token now. You won&apos;t be able to see it again!
            </p>
          </div>

          <div className="relative">
            <div className="p-3 bg-gray-900 rounded-lg font-mono text-sm text-gray-300 break-all">
              {generatedToken}
            </div>
          </div>

          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={handleCopyToken}>
              {copied ? (
                <>
                  <Check className="w-4 h-4 mr-2" />
                  Copied!
                </>
              ) : (
                <>
                  <Copy className="w-4 h-4 mr-2" />
                  Copy to Clipboard
                </>
              )}
            </Button>
            <Button
              onClick={() => {
                setShowTokenModal(false);
                setGeneratedToken(null);
              }}
            >
              Done
            </Button>
          </div>
        </div>
      </Modal>

      {/* Notification Settings Modal */}
      <Modal
        isOpen={showNotificationModal}
        onClose={() => setShowNotificationModal(false)}
        title="Notification Settings"
        size="md"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            updateNotificationsMutation.mutate();
          }}
          className="space-y-4"
        >
          <Input
            label="Default Notification Threshold"
            type="number"
            placeholder="Token threshold for notifications"
            value={notificationSettings.defaultNotificationThreshold || ''}
            onChange={(e) =>
              setNotificationSettings({
                ...notificationSettings,
                defaultNotificationThreshold: parseInt(e.target.value) || 0,
              })
            }
          />

          <Input
            label="Webhook URL"
            type="url"
            placeholder="https://example.com/webhook"
            value={notificationSettings.webhookUrl}
            onChange={(e) =>
              setNotificationSettings({
                ...notificationSettings,
                webhookUrl: e.target.value,
              })
            }
          />

          <Input
            label="Notification Email"
            type="email"
            placeholder="admin@example.com"
            value={notificationSettings.email}
            onChange={(e) =>
              setNotificationSettings({
                ...notificationSettings,
                email: e.target.value,
              })
            }
          />

          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setShowNotificationModal(false)}
            >
              Cancel
            </Button>
            <Button type="submit" loading={updateNotificationsMutation.isPending}>
              Save Settings
            </Button>
          </div>
        </form>
      </Modal>

      {/* Assign Agent Type Modal */}
      <Modal
        isOpen={showAgentModal}
        onClose={() => {
          setShowAgentModal(false);
          setNewAgentAssignment({ agentType: '', customTokenLimit: '', priority: '' });
        }}
        title="Assign Agent Type"
        size="md"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            assignAgentTypeMutation.mutate();
          }}
          className="space-y-4"
        >
          <div>
            <label className="block text-sm font-medium text-gray-300 mb-2">Agent Type</label>
            <select
              className="w-full px-3 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              value={newAgentAssignment.agentType}
              onChange={(e) =>
                setNewAgentAssignment({ ...newAgentAssignment, agentType: e.target.value })
              }
              required
            >
              <option value="">Select an agent type...</option>
              {availableAgentTypes.map((at) => (
                <option key={at.agentType} value={at.agentType}>
                  {at.name} ({at.agentType})
                </option>
              ))}
            </select>
          </div>

          <Input
            label="Custom Token Limit (optional)"
            type="number"
            placeholder="Leave empty for default"
            value={newAgentAssignment.customTokenLimit}
            onChange={(e) =>
              setNewAgentAssignment({ ...newAgentAssignment, customTokenLimit: e.target.value })
            }
          />

          <Input
            label="Priority (optional)"
            type="number"
            placeholder="Lower number = higher priority"
            value={newAgentAssignment.priority}
            onChange={(e) =>
              setNewAgentAssignment({ ...newAgentAssignment, priority: e.target.value })
            }
          />

          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setShowAgentModal(false);
                setNewAgentAssignment({ agentType: '', customTokenLimit: '', priority: '' });
              }}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              loading={assignAgentTypeMutation.isPending}
              disabled={!newAgentAssignment.agentType}
            >
              Assign Agent
            </Button>
          </div>
        </form>
      </Modal>

      {/* Edit Agent Assignment Modal */}
      <Modal
        isOpen={showEditAgentModal}
        onClose={() => {
          setShowEditAgentModal(false);
          setSelectedAgentAssignment(null);
        }}
        title={`Edit ${selectedAgentAssignment?.agentName || 'Agent'} Assignment`}
        size="md"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            updateAgentAssignmentMutation.mutate();
          }}
          className="space-y-4"
        >
          <div className="flex items-center justify-between p-3 bg-gray-800 rounded-lg">
            <span className="text-gray-300">Enabled</span>
            <Toggle
              checked={editAgentSettings.enabled}
              onChange={(checked) => setEditAgentSettings({ ...editAgentSettings, enabled: checked })}
            />
          </div>

          <Input
            label="Custom Token Limit"
            type="number"
            placeholder="Leave empty for default"
            value={editAgentSettings.customTokenLimit}
            onChange={(e) =>
              setEditAgentSettings({ ...editAgentSettings, customTokenLimit: e.target.value })
            }
          />

          <Input
            label="Priority"
            type="number"
            placeholder="Lower number = higher priority"
            value={editAgentSettings.priority}
            onChange={(e) =>
              setEditAgentSettings({ ...editAgentSettings, priority: e.target.value })
            }
          />

          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setShowEditAgentModal(false);
                setSelectedAgentAssignment(null);
              }}
            >
              Cancel
            </Button>
            <Button type="submit" loading={updateAgentAssignmentMutation.isPending}>
              Save Changes
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
