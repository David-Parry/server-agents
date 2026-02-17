'use client';

import { useState } from 'react';
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
  StatCard,
} from '@/components/ui';
import {
  Bot,
  Settings,
  Users,
  Zap,
  Clock,
  RefreshCw,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import { format } from 'date-fns';
import { AgentConfigResponse } from '@/lib/types';

export default function AgentsPage() {
  const queryClient = useQueryClient();
  const [showEditModal, setShowEditModal] = useState(false);
  const [showCustomersModal, setShowCustomersModal] = useState(false);
  const [selectedAgent, setSelectedAgent] = useState<AgentConfigResponse | null>(null);
  const [expandedAgent, setExpandedAgent] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({
    name: '',
    description: '',
    model: '',
    enabled: true,
    maxTokens: '',
    temperature: '',
    timeoutSeconds: '',
    retryAttempts: '',
    retryDelayMs: '',
  });

  const { data: agentTypes, isLoading } = useQuery({
    queryKey: ['agent-types'],
    queryFn: () => api.listAgentTypes(),
  });

  const { data: agentStats } = useQuery({
    queryKey: ['agent-type-stats'],
    queryFn: () => api.getAgentTypeStatistics(),
  });

  const { data: models } = useQuery({
    queryKey: ['models'],
    queryFn: () => api.listModels(),
  });

  const { data: agentCustomers, isLoading: isLoadingCustomers } = useQuery({
    queryKey: ['agent-customers', selectedAgent?.agentType],
    queryFn: () => api.getCustomersByAgentType(selectedAgent!.agentType),
    enabled: !!selectedAgent && showCustomersModal,
  });

  const updateAgentMutation = useMutation({
    mutationFn: () =>
      api.updateAgentType(selectedAgent!.agentType, {
        name: editForm.name || undefined,
        description: editForm.description || undefined,
        model: editForm.model || undefined,
        enabled: editForm.enabled,
        maxTokens: editForm.maxTokens ? parseInt(editForm.maxTokens) : undefined,
        temperature: editForm.temperature ? parseFloat(editForm.temperature) : undefined,
        timeoutSeconds: editForm.timeoutSeconds ? parseInt(editForm.timeoutSeconds) : undefined,
        retryAttempts: editForm.retryAttempts ? parseInt(editForm.retryAttempts) : undefined,
        retryDelayMs: editForm.retryDelayMs ? parseInt(editForm.retryDelayMs) : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent-types'] });
      setShowEditModal(false);
      setSelectedAgent(null);
    },
  });

  const enableAgentMutation = useMutation({
    mutationFn: (agentType: string) => api.enableAgentType(agentType),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent-types'] });
    },
  });

  const disableAgentMutation = useMutation({
    mutationFn: (agentType: string) => api.disableAgentType(agentType),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent-types'] });
    },
  });

  const handleEditAgent = (agent: AgentConfigResponse) => {
    setSelectedAgent(agent);
    setEditForm({
      name: agent.name,
      description: agent.description || '',
      model: agent.model,
      enabled: agent.enabled,
      maxTokens: agent.executionConfig?.maxTokens?.toString() || '',
      temperature: agent.executionConfig?.temperature?.toString() || '',
      timeoutSeconds: agent.executionConfig?.timeoutSeconds?.toString() || '',
      retryAttempts: agent.executionConfig?.retryAttempts?.toString() || '',
      retryDelayMs: agent.executionConfig?.retryDelayMs?.toString() || '',
    });
    setShowEditModal(true);
  };

  const handleViewCustomers = (agent: AgentConfigResponse) => {
    setSelectedAgent(agent);
    setShowCustomersModal(true);
  };

  const toggleExpanded = (agentType: string) => {
    setExpandedAgent(expandedAgent === agentType ? null : agentType);
  };

  const enabledAgents = agentTypes?.filter((a) => a.enabled).length || 0;
  const totalAgents = agentTypes?.length || 0;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner className="w-8 h-8" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Agent Types</h1>
          <p className="text-gray-400 mt-1">
            Manage AI agent configurations and customer assignments
          </p>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard
          title="Total Agent Types"
          value={totalAgents}
          icon={<Bot className="w-8 h-8" />}
        />
        <StatCard
          title="Enabled Agents"
          value={enabledAgents}
          icon={<Zap className="w-8 h-8" />}
        />
        <StatCard
          title="Total Assignments"
          value={agentStats?.totalAssignments || 0}
          icon={<Users className="w-8 h-8" />}
        />
        <StatCard
          title="Avg Assignments"
          value={
            totalAgents > 0
              ? Math.round((agentStats?.totalAssignments || 0) / totalAgents)
              : 0
          }
          icon={<RefreshCw className="w-8 h-8" />}
        />
      </div>

      {/* Agent Types List */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white">Agent Configurations</h2>
        </CardHeader>
        {agentTypes && agentTypes.length > 0 ? (
          <div className="divide-y divide-gray-700">
            {agentTypes.map((agent) => (
              <div key={agent.id}>
                {/* Agent Row */}
                <div
                  className="px-6 py-4 hover:bg-gray-800/50 cursor-pointer transition-colors"
                  onClick={() => toggleExpanded(agent.agentType)}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                      <div className="p-2 bg-purple-900/30 rounded-lg">
                        <Bot className="w-5 h-5 text-purple-400" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <h3 className="font-medium text-white">{agent.name}</h3>
                          <code className="px-2 py-0.5 bg-gray-800 rounded text-xs text-purple-400">
                            {agent.agentType}
                          </code>
                        </div>
                        <p className="text-sm text-gray-400 mt-0.5">
                          {agent.description || 'No description'}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <Badge variant={agent.enabled ? 'success' : 'danger'}>
                        {agent.enabled ? 'Enabled' : 'Disabled'}
                      </Badge>
                      <div className="text-sm text-gray-400">
                        {agentStats?.customerCountByAgentType?.[agent.agentType] || 0} customers
                      </div>
                      {expandedAgent === agent.agentType ? (
                        <ChevronUp className="w-5 h-5 text-gray-400" />
                      ) : (
                        <ChevronDown className="w-5 h-5 text-gray-400" />
                      )}
                    </div>
                  </div>
                </div>

                {/* Expanded Details */}
                {expandedAgent === agent.agentType && (
                  <div className="px-6 py-4 bg-gray-800/30 border-t border-gray-700">
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                      {/* Model Info */}
                      <div>
                        <h4 className="text-sm font-medium text-gray-400 mb-2">Model</h4>
                        <p className="text-white">
                          {agent.modelDisplayName || agent.model}
                        </p>
                        <p className="text-xs text-gray-500 mt-1">{agent.model}</p>
                      </div>

                      {/* Execution Config */}
                      <div>
                        <h4 className="text-sm font-medium text-gray-400 mb-2">
                          Execution Settings
                        </h4>
                        <div className="space-y-1 text-sm">
                          <div className="flex justify-between">
                            <span className="text-gray-400">Max Tokens:</span>
                            <span className="text-white">
                              {agent.executionConfig?.maxTokens?.toLocaleString() || 'Default'}
                            </span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-gray-400">Temperature:</span>
                            <span className="text-white">
                              {agent.executionConfig?.temperature ?? 'Default'}
                            </span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-gray-400">Timeout:</span>
                            <span className="text-white">
                              {agent.executionConfig?.timeoutSeconds
                                ? `${agent.executionConfig.timeoutSeconds}s`
                                : 'Default'}
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Retry Config */}
                      <div>
                        <h4 className="text-sm font-medium text-gray-400 mb-2">Retry Settings</h4>
                        <div className="space-y-1 text-sm">
                          <div className="flex justify-between">
                            <span className="text-gray-400">Attempts:</span>
                            <span className="text-white">
                              {agent.executionConfig?.retryAttempts ?? 'Default'}
                            </span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-gray-400">Delay:</span>
                            <span className="text-white">
                              {agent.executionConfig?.retryDelayMs
                                ? `${agent.executionConfig.retryDelayMs}ms`
                                : 'Default'}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Metadata */}
                    <div className="mt-4 pt-4 border-t border-gray-700 flex items-center justify-between">
                      <div className="text-sm text-gray-400">
                        <span>Version {agent.version}</span>
                        <span className="mx-2">•</span>
                        <span>Updated {format(new Date(agent.updatedAt), 'MMM d, yyyy HH:mm')}</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleViewCustomers(agent);
                          }}
                        >
                          <Users className="w-4 h-4 mr-2" />
                          View Customers
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleEditAgent(agent);
                          }}
                        >
                          <Settings className="w-4 h-4 mr-2" />
                          Edit
                        </Button>
                        {agent.enabled ? (
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              disableAgentMutation.mutate(agent.agentType);
                            }}
                            loading={disableAgentMutation.isPending}
                          >
                            Disable
                          </Button>
                        ) : (
                          <Button
                            variant="primary"
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              enableAgentMutation.mutate(agent.agentType);
                            }}
                            loading={enableAgentMutation.isPending}
                          >
                            Enable
                          </Button>
                        )}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        ) : (
          <CardContent>
            <p className="text-gray-400 text-center py-8">No agent types configured</p>
          </CardContent>
        )}
      </Card>

      {/* Edit Agent Modal */}
      <Modal
        isOpen={showEditModal}
        onClose={() => {
          setShowEditModal(false);
          setSelectedAgent(null);
        }}
        title={`Edit ${selectedAgent?.name || 'Agent'}`}
        size="lg"
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            updateAgentMutation.mutate();
          }}
          className="space-y-4"
        >
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Name"
              value={editForm.name}
              onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
            />
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">Model</label>
              <select
                className="w-full px-3 py-2 bg-gray-800 border border-gray-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={editForm.model}
                onChange={(e) => setEditForm({ ...editForm, model: e.target.value })}
              >
                {models?.map((m) => (
                  <option key={m.model} value={m.model}>
                    {m.displayName || m.model}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <Input
            label="Description"
            value={editForm.description}
            onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
          />

          <div className="flex items-center justify-between p-3 bg-gray-800 rounded-lg">
            <span className="text-gray-300">Enabled</span>
            <Toggle
              checked={editForm.enabled}
              onChange={(checked) => setEditForm({ ...editForm, enabled: checked })}
            />
          </div>

          <div className="border-t border-gray-700 pt-4">
            <h4 className="text-sm font-medium text-gray-300 mb-3">Execution Settings</h4>
            <div className="grid grid-cols-2 gap-4">
              <Input
                label="Max Tokens"
                type="number"
                placeholder="Default"
                value={editForm.maxTokens}
                onChange={(e) => setEditForm({ ...editForm, maxTokens: e.target.value })}
              />
              <Input
                label="Temperature"
                type="number"
                step="0.1"
                min="0"
                max="2"
                placeholder="Default"
                value={editForm.temperature}
                onChange={(e) => setEditForm({ ...editForm, temperature: e.target.value })}
              />
              <Input
                label="Timeout (seconds)"
                type="number"
                placeholder="Default"
                value={editForm.timeoutSeconds}
                onChange={(e) => setEditForm({ ...editForm, timeoutSeconds: e.target.value })}
              />
              <Input
                label="Retry Attempts"
                type="number"
                placeholder="Default"
                value={editForm.retryAttempts}
                onChange={(e) => setEditForm({ ...editForm, retryAttempts: e.target.value })}
              />
              <Input
                label="Retry Delay (ms)"
                type="number"
                placeholder="Default"
                value={editForm.retryDelayMs}
                onChange={(e) => setEditForm({ ...editForm, retryDelayMs: e.target.value })}
              />
            </div>
          </div>

          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setShowEditModal(false);
                setSelectedAgent(null);
              }}
            >
              Cancel
            </Button>
            <Button type="submit" loading={updateAgentMutation.isPending}>
              Save Changes
            </Button>
          </div>
        </form>
      </Modal>

      {/* View Customers Modal */}
      <Modal
        isOpen={showCustomersModal}
        onClose={() => {
          setShowCustomersModal(false);
          setSelectedAgent(null);
        }}
        title={`Customers using ${selectedAgent?.name || 'Agent'}`}
        size="lg"
      >
        {isLoadingCustomers ? (
          <div className="flex items-center justify-center py-8">
            <Spinner className="w-6 h-6" />
          </div>
        ) : agentCustomers?.customers && agentCustomers.customers.length > 0 ? (
          <div className="space-y-4">
            <div className="flex items-center gap-4 text-sm text-gray-400">
              <span>Total: {agentCustomers.totalCustomers}</span>
              <span>•</span>
              <span>Enabled: {agentCustomers.enabledCount}</span>
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Customer</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Token Limit</TableHead>
                  <TableHead>Priority</TableHead>
                  <TableHead>Assigned</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {agentCustomers.customers.map((customer) => (
                  <TableRow key={customer.id}>
                    <TableCell>
                      <div>
                        <p className="font-medium text-white">{customer.customerName}</p>
                        <p className="text-xs text-gray-500 font-mono">{customer.customerId}</p>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={customer.enabled ? 'success' : 'danger'}>
                        {customer.enabled ? 'Enabled' : 'Disabled'}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {customer.customTokenLimit ? (
                        <span className="text-white">
                          {customer.customTokenLimit.toLocaleString()}
                        </span>
                      ) : (
                        <span className="text-gray-500">Default</span>
                      )}
                    </TableCell>
                    <TableCell>
                      {customer.priority !== undefined && customer.priority !== null ? (
                        <span className="text-white">{customer.priority}</span>
                      ) : (
                        <span className="text-gray-500">—</span>
                      )}
                    </TableCell>
                    <TableCell className="text-gray-400 text-sm">
                      {format(new Date(customer.createdAt), 'MMM d, yyyy')}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : (
          <p className="text-gray-400 text-center py-8">
            No customers assigned to this agent type
          </p>
        )}
        <div className="flex justify-end pt-4">
          <Button
            variant="secondary"
            onClick={() => {
              setShowCustomersModal(false);
              setSelectedAgent(null);
            }}
          >
            Close
          </Button>
        </div>
      </Modal>
    </div>
  );
}
