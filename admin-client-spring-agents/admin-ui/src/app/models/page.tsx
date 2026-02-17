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
  EmptyState,
  Toggle,
} from '@/components/ui';
import { Plus, Cpu, Edit, Trash2, RefreshCw, Link as LinkIcon, Users } from 'lucide-react';
import { format } from 'date-fns';
import { CreateModelRequest, UpdateModelRequest, ModelResponse } from '@/lib/types';

export default function ModelsPage() {
  const queryClient = useQueryClient();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showAllowancesModal, setShowAllowancesModal] = useState(false);
  const [selectedModel, setSelectedModel] = useState<ModelResponse | null>(null);

  // Form state
  const [newModel, setNewModel] = useState<CreateModelRequest>({
    model: '',
    provider: '',
    displayName: '',
    description: '',
    defaultTokensForNewCustomers: undefined,
    inputTokenPricePerMillion: undefined,
    outputTokenPricePerMillion: undefined,
  });

  const [editModel, setEditModel] = useState<UpdateModelRequest>({
    displayName: '',
    description: '',
    defaultTokensForNewCustomers: undefined,
    enabled: true,
    inputTokenPricePerMillion: undefined,
    outputTokenPricePerMillion: undefined,
  });

  const { data: models, isLoading } = useQuery({
    queryKey: ['models'],
    queryFn: () => api.listModels(),
  });

  const { data: modelAllowances, isLoading: allowancesLoading } = useQuery({
    queryKey: ['model-allowances', selectedModel?.model],
    queryFn: () => api.getAllowancesByModel(selectedModel!.model),
    enabled: !!selectedModel && showAllowancesModal,
  });

  const createMutation = useMutation({
    mutationFn: (data: CreateModelRequest) => api.createModel(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['models'] });
      setShowCreateModal(false);
      setNewModel({
        model: '',
        provider: '',
        displayName: '',
        description: '',
        defaultTokensForNewCustomers: undefined,
        inputTokenPricePerMillion: undefined,
        outputTokenPricePerMillion: undefined,
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ model, data }: { model: string; data: UpdateModelRequest }) =>
      api.updateModel(model, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['models'] });
      setShowEditModal(false);
      setSelectedModel(null);
    },
  });

  const enableMutation = useMutation({
    mutationFn: (model: string) => api.enableModel(model),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['models'] });
    },
  });

  const disableMutation = useMutation({
    mutationFn: (model: string) => api.disableModel(model),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['models'] });
    },
  });

  const linkAllMutation = useMutation({
    mutationFn: (model: string) => api.linkModelToAllCustomers(model),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['models'] });
    },
  });

  const resetUsageMutation = useMutation({
    mutationFn: (model: string) => api.resetAllUsageForModel(model),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['models'] });
    },
  });

  const handleCreateModel = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate(newModel);
  };

  const handleUpdateModel = (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedModel) {
      updateMutation.mutate({ model: selectedModel.model, data: editModel });
    }
  };

  const openEditModal = (model: ModelResponse) => {
    setSelectedModel(model);
    setEditModel({
      displayName: model.displayName || '',
      description: model.description || '',
      defaultTokensForNewCustomers: model.defaultTokensForNewCustomers,
      enabled: model.enabled,
      inputTokenPricePerMillion: model.inputTokenPricePerMillion,
      outputTokenPricePerMillion: model.outputTokenPricePerMillion,
    });
    setShowEditModal(true);
  };

  const openAllowancesModal = (model: ModelResponse) => {
    setSelectedModel(model);
    setShowAllowancesModal(true);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Models</h1>
          <p className="text-gray-400 mt-1">Manage LLM models and their configurations</p>
        </div>
        <Button onClick={() => setShowCreateModal(true)}>
          <Plus className="w-4 h-4 mr-2" />
          Add Model
        </Button>
      </div>

      {/* Models Table */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white flex items-center gap-2">
            <Cpu className="w-5 h-5 text-green-400" />
            Model List
          </h2>
        </CardHeader>
        {isLoading ? (
          <div className="flex justify-center py-12">
            <Spinner className="w-8 h-8" />
          </div>
        ) : models && models.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Model</TableHead>
                <TableHead>Provider</TableHead>
                <TableHead>Default Tokens</TableHead>
                <TableHead>Input Price/M</TableHead>
                <TableHead>Output Price/M</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {models.map((model) => (
                <TableRow key={model.model}>
                  <TableCell>
                    <div>
                      <p className="font-medium text-white">
                        {model.displayName || model.model}
                      </p>
                      <p className="text-xs text-gray-500">{model.model}</p>
                      {model.description && (
                        <p className="text-xs text-gray-400 mt-1">{model.description}</p>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="info">{model.provider}</Badge>
                  </TableCell>
                  <TableCell>
                    {model.defaultTokensForNewCustomers
                      ? model.defaultTokensForNewCustomers.toLocaleString()
                      : 'Unlimited'}
                  </TableCell>
                  <TableCell>
                    <span className="text-gray-300">
                      ${model.inputTokenPricePerMillion?.toFixed(2) ?? '0.00'}
                    </span>
                  </TableCell>
                  <TableCell>
                    <span className="text-gray-300">
                      ${model.outputTokenPricePerMillion?.toFixed(2) ?? '0.00'}
                    </span>
                  </TableCell>
                  <TableCell>
                    <Badge variant={model.enabled ? 'success' : 'danger'}>
                      {model.enabled ? 'Enabled' : 'Disabled'}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {format(new Date(model.createdAt), 'MMM d, yyyy')}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        title="Edit Model"
                        onClick={() => openEditModal(model)}
                      >
                        <Edit className="w-4 h-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        title="View Allowances"
                        onClick={() => openAllowancesModal(model)}
                      >
                        <Users className="w-4 h-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        title="Link to All Customers"
                        onClick={() => linkAllMutation.mutate(model.model)}
                      >
                        <LinkIcon className="w-4 h-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        title="Reset All Usage"
                        onClick={() => resetUsageMutation.mutate(model.model)}
                      >
                        <RefreshCw className="w-4 h-4" />
                      </Button>
                      {model.enabled ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          title="Disable Model"
                          onClick={() => disableMutation.mutate(model.model)}
                        >
                          <Trash2 className="w-4 h-4 text-red-400" />
                        </Button>
                      ) : (
                        <Button
                          variant="ghost"
                          size="sm"
                          title="Enable Model"
                          onClick={() => enableMutation.mutate(model.model)}
                        >
                          <RefreshCw className="w-4 h-4 text-green-400" />
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <CardContent>
            <EmptyState
              icon={<Cpu className="w-12 h-12" />}
              title="No models found"
              description="Get started by adding your first LLM model"
              action={
                <Button onClick={() => setShowCreateModal(true)}>
                  <Plus className="w-4 h-4 mr-2" />
                  Add Model
                </Button>
              }
            />
          </CardContent>
        )}
      </Card>

      {/* Create Model Modal */}
      <Modal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Add New Model"
        size="md"
      >
        <form onSubmit={handleCreateModel} className="space-y-4">
          <Input
            label="Model ID"
            placeholder="e.g., claude-sonnet-4-5"
            value={newModel.model}
            onChange={(e) => setNewModel({ ...newModel, model: e.target.value })}
            required
          />

          <Input
            label="Provider"
            placeholder="e.g., anthropic, openai"
            value={newModel.provider}
            onChange={(e) => setNewModel({ ...newModel, provider: e.target.value })}
            required
          />

          <Input
            label="Display Name"
            placeholder="e.g., Claude Sonnet 4.5"
            value={newModel.displayName || ''}
            onChange={(e) => setNewModel({ ...newModel, displayName: e.target.value })}
          />

          <Input
            label="Description"
            placeholder="Model description"
            value={newModel.description || ''}
            onChange={(e) => setNewModel({ ...newModel, description: e.target.value })}
          />

          <Input
            label="Default Tokens for New Customers"
            type="number"
            placeholder="Leave empty for unlimited"
            value={newModel.defaultTokensForNewCustomers || ''}
            onChange={(e) =>
              setNewModel({
                ...newModel,
                defaultTokensForNewCustomers: parseInt(e.target.value) || undefined,
              })
            }
          />

          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Input Token Price / Million"
              type="number"
              step="0.01"
              placeholder="e.g., 3.00"
              value={newModel.inputTokenPricePerMillion ?? ''}
              onChange={(e) =>
                setNewModel({
                  ...newModel,
                  inputTokenPricePerMillion: parseFloat(e.target.value) || undefined,
                })
              }
            />
            <Input
              label="Output Token Price / Million"
              type="number"
              step="0.01"
              placeholder="e.g., 15.00"
              value={newModel.outputTokenPricePerMillion ?? ''}
              onChange={(e) =>
                setNewModel({
                  ...newModel,
                  outputTokenPricePerMillion: parseFloat(e.target.value) || undefined,
                })
              }
            />
          </div>

          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setShowCreateModal(false)}
            >
              Cancel
            </Button>
            <Button type="submit" loading={createMutation.isPending}>
              Create Model
            </Button>
          </div>
        </form>
      </Modal>

      {/* Edit Model Modal */}
      <Modal
        isOpen={showEditModal}
        onClose={() => {
          setShowEditModal(false);
          setSelectedModel(null);
        }}
        title={`Edit Model: ${selectedModel?.model}`}
        size="md"
      >
        <form onSubmit={handleUpdateModel} className="space-y-4">
          <Input
            label="Display Name"
            placeholder="e.g., Claude Sonnet 4.5"
            value={editModel.displayName || ''}
            onChange={(e) => setEditModel({ ...editModel, displayName: e.target.value })}
          />

          <Input
            label="Description"
            placeholder="Model description"
            value={editModel.description || ''}
            onChange={(e) => setEditModel({ ...editModel, description: e.target.value })}
          />

          <Input
            label="Default Tokens for New Customers"
            type="number"
            placeholder="Leave empty for unlimited"
            value={editModel.defaultTokensForNewCustomers || ''}
            onChange={(e) =>
              setEditModel({
                ...editModel,
                defaultTokensForNewCustomers: parseInt(e.target.value) || undefined,
              })
            }
          />

          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Input Token Price / Million"
              type="number"
              step="0.01"
              placeholder="e.g., 3.00"
              value={editModel.inputTokenPricePerMillion ?? ''}
              onChange={(e) =>
                setEditModel({
                  ...editModel,
                  inputTokenPricePerMillion: parseFloat(e.target.value) || undefined,
                })
              }
            />
            <Input
              label="Output Token Price / Million"
              type="number"
              step="0.01"
              placeholder="e.g., 15.00"
              value={editModel.outputTokenPricePerMillion ?? ''}
              onChange={(e) =>
                setEditModel({
                  ...editModel,
                  outputTokenPricePerMillion: parseFloat(e.target.value) || undefined,
                })
              }
            />
          </div>

          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-300">Enabled</span>
            <Toggle
              checked={editModel.enabled ?? true}
              onChange={(checked) => setEditModel({ ...editModel, enabled: checked })}
            />
          </div>

          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setShowEditModal(false);
                setSelectedModel(null);
              }}
            >
              Cancel
            </Button>
            <Button type="submit" loading={updateMutation.isPending}>
              Save Changes
            </Button>
          </div>
        </form>
      </Modal>

      {/* Model Allowances Modal */}
      <Modal
        isOpen={showAllowancesModal}
        onClose={() => {
          setShowAllowancesModal(false);
          setSelectedModel(null);
        }}
        title={`Allowances: ${selectedModel?.displayName || selectedModel?.model}`}
        size="xl"
      >
        {allowancesLoading ? (
          <div className="flex justify-center py-8">
            <Spinner />
          </div>
        ) : modelAllowances ? (
          <div className="space-y-4">
            <div className="flex items-center justify-between text-sm">
              <span className="text-gray-400">Total Allowances</span>
              <Badge variant="info">{modelAllowances.totalAllowances}</Badge>
            </div>

            {modelAllowances.allowances.length > 0 ? (
              <div className="max-h-96 overflow-y-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Customer</TableHead>
                      <TableHead>Policy</TableHead>
                      <TableHead>Usage</TableHead>
                      <TableHead>Status</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {modelAllowances.allowances.map((allowance) => (
                      <TableRow key={allowance.customerId}>
                        <TableCell>
                          <div>
                            <p className="font-medium text-white">{allowance.customerName}</p>
                            <p className="text-xs text-gray-500 font-mono">
                              {allowance.customerId}
                            </p>
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={allowance.unlimited ? 'success' : 'info'}>
                            {allowance.policyTypeName}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {allowance.unlimited ? (
                            <span className="text-gray-400">Unlimited</span>
                          ) : (
                            <span>
                              {allowance.tokensUsed.toLocaleString()} /{' '}
                              {allowance.allowedTokens.toLocaleString()}
                            </span>
                          )}
                        </TableCell>
                        <TableCell>
                          <div className="flex gap-2">
                            <Badge
                              variant={allowance.customerEnabled ? 'success' : 'danger'}
                            >
                              {allowance.customerEnabled ? 'Customer OK' : 'Customer Disabled'}
                            </Badge>
                            <Badge
                              variant={allowance.allowanceEnabled ? 'success' : 'danger'}
                            >
                              {allowance.allowanceEnabled ? 'Allowance OK' : 'Allowance Disabled'}
                            </Badge>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            ) : (
              <p className="text-gray-400 text-center py-4">
                No customers have allowances for this model
              </p>
            )}

            <div className="flex justify-end pt-4">
              <Button
                onClick={() => {
                  setShowAllowancesModal(false);
                  setSelectedModel(null);
                }}
              >
                Close
              </Button>
            </div>
          </div>
        ) : (
          <p className="text-gray-400 text-center py-4">Failed to load allowances</p>
        )}
      </Modal>
    </div>
  );
}
