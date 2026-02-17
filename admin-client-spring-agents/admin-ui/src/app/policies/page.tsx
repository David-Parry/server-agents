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
import { Plus, Shield, Edit, Trash2, RefreshCw, Infinity } from 'lucide-react';
import { CreatePolicyTypeRequest, UpdatePolicyTypeRequest, PolicyTypeResponse } from '@/lib/types';

export default function PoliciesPage() {
  const queryClient = useQueryClient();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [selectedPolicy, setSelectedPolicy] = useState<PolicyTypeResponse | null>(null);

  // Form state
  const [newPolicy, setNewPolicy] = useState<CreatePolicyTypeRequest>({
    name: '',
    description: '',
    resetDays: undefined,
  });

  const [editPolicy, setEditPolicy] = useState<UpdatePolicyTypeRequest>({
    description: '',
    resetDays: undefined,
    enabled: true,
  });

  const { data: policies, isLoading } = useQuery({
    queryKey: ['policy-types'],
    queryFn: () => api.listPolicyTypes(),
  });

  const createMutation = useMutation({
    mutationFn: (data: CreatePolicyTypeRequest) => api.createPolicyType(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policy-types'] });
      setShowCreateModal(false);
      setNewPolicy({ name: '', description: '', resetDays: undefined });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdatePolicyTypeRequest }) =>
      api.updatePolicyType(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policy-types'] });
      setShowEditModal(false);
      setSelectedPolicy(null);
    },
  });

  const disableMutation = useMutation({
    mutationFn: (id: string) => api.disablePolicyType(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policy-types'] });
    },
  });

  const handleCreatePolicy = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate(newPolicy);
  };

  const handleUpdatePolicy = (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedPolicy) {
      updateMutation.mutate({ id: selectedPolicy.id, data: editPolicy });
    }
  };

  const openEditModal = (policy: PolicyTypeResponse) => {
    setSelectedPolicy(policy);
    setEditPolicy({
      description: policy.description || '',
      resetDays: policy.resetDays,
      enabled: policy.enabled,
    });
    setShowEditModal(true);
  };

  const getResetPeriodLabel = (days: number | undefined) => {
    if (!days) return 'Unlimited';
    if (days === 1) return 'Daily';
    if (days === 7) return 'Weekly';
    if (days === 30) return 'Monthly';
    if (days === 365) return 'Yearly';
    return `${days} days`;
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Policy Types</h1>
          <p className="text-gray-400 mt-1">Manage token reset policies for customer allowances</p>
        </div>
        <Button onClick={() => setShowCreateModal(true)}>
          <Plus className="w-4 h-4 mr-2" />
          Add Policy Type
        </Button>
      </div>

      {/* Info Card */}
      <Card>
        <CardContent className="py-4">
          <div className="flex items-start gap-3">
            <Shield className="w-5 h-5 text-yellow-400 mt-0.5" />
            <div>
              <p className="text-sm text-gray-300">
                Policy types define how token allowances reset over time. An unlimited policy means
                tokens never reset and usage accumulates indefinitely. Policies with reset days will
                automatically reset token usage after the specified period.
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Policies Table */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white flex items-center gap-2">
            <Shield className="w-5 h-5 text-yellow-400" />
            Policy Type List
          </h2>
        </CardHeader>
        {isLoading ? (
          <div className="flex justify-center py-12">
            <Spinner className="w-8 h-8" />
          </div>
        ) : policies && policies.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Description</TableHead>
                <TableHead>Reset Period</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {policies.map((policy) => (
                <TableRow key={policy.id}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <p className="font-medium text-white">{policy.name}</p>
                      {!policy.resetDays && (
                        <Infinity className="w-4 h-4 text-green-400" />
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    <p className="text-gray-400">{policy.description || '-'}</p>
                  </TableCell>
                  <TableCell>
                    <Badge variant={policy.resetDays ? 'info' : 'success'}>
                      {getResetPeriodLabel(policy.resetDays)}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={policy.enabled ? 'success' : 'danger'}>
                      {policy.enabled ? 'Enabled' : 'Disabled'}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        title="Edit Policy"
                        onClick={() => openEditModal(policy)}
                      >
                        <Edit className="w-4 h-4" />
                      </Button>
                      {policy.enabled && policy.name !== 'UNLIMITED' && (
                        <Button
                          variant="ghost"
                          size="sm"
                          title="Disable Policy"
                          onClick={() => disableMutation.mutate(policy.id)}
                        >
                          <Trash2 className="w-4 h-4 text-red-400" />
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
              icon={<Shield className="w-12 h-12" />}
              title="No policy types found"
              description="Get started by creating your first policy type"
              action={
                <Button onClick={() => setShowCreateModal(true)}>
                  <Plus className="w-4 h-4 mr-2" />
                  Add Policy Type
                </Button>
              }
            />
          </CardContent>
        )}
      </Card>

      {/* Common Policy Templates */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white">Common Policy Templates</h2>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="p-4 bg-gray-800/50 rounded-lg">
              <h3 className="font-medium text-white">Daily</h3>
              <p className="text-sm text-gray-400 mt-1">Reset every day</p>
              <p className="text-xs text-gray-500 mt-2">resetDays: 1</p>
            </div>
            <div className="p-4 bg-gray-800/50 rounded-lg">
              <h3 className="font-medium text-white">Weekly</h3>
              <p className="text-sm text-gray-400 mt-1">Reset every 7 days</p>
              <p className="text-xs text-gray-500 mt-2">resetDays: 7</p>
            </div>
            <div className="p-4 bg-gray-800/50 rounded-lg">
              <h3 className="font-medium text-white">Monthly</h3>
              <p className="text-sm text-gray-400 mt-1">Reset every 30 days</p>
              <p className="text-xs text-gray-500 mt-2">resetDays: 30</p>
            </div>
            <div className="p-4 bg-gray-800/50 rounded-lg">
              <h3 className="font-medium text-white flex items-center gap-2">
                Unlimited
                <Infinity className="w-4 h-4 text-green-400" />
              </h3>
              <p className="text-sm text-gray-400 mt-1">Never resets</p>
              <p className="text-xs text-gray-500 mt-2">resetDays: null</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Create Policy Modal */}
      <Modal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Create Policy Type"
        size="md"
      >
        <form onSubmit={handleCreatePolicy} className="space-y-4">
          <Input
            label="Policy Name"
            placeholder="e.g., MONTHLY, WEEKLY"
            value={newPolicy.name}
            onChange={(e) =>
              setNewPolicy({ ...newPolicy, name: e.target.value.toUpperCase() })
            }
            required
          />

          <Input
            label="Description"
            placeholder="Describe this policy type"
            value={newPolicy.description || ''}
            onChange={(e) => setNewPolicy({ ...newPolicy, description: e.target.value })}
          />

          <Input
            label="Reset Days"
            type="number"
            placeholder="Leave empty for unlimited"
            value={newPolicy.resetDays || ''}
            onChange={(e) =>
              setNewPolicy({
                ...newPolicy,
                resetDays: parseInt(e.target.value) || undefined,
              })
            }
          />

          <p className="text-xs text-gray-500">
            Leave reset days empty to create an unlimited policy where tokens never reset.
          </p>

          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setShowCreateModal(false)}
            >
              Cancel
            </Button>
            <Button type="submit" loading={createMutation.isPending}>
              Create Policy
            </Button>
          </div>
        </form>
      </Modal>

      {/* Edit Policy Modal */}
      <Modal
        isOpen={showEditModal}
        onClose={() => {
          setShowEditModal(false);
          setSelectedPolicy(null);
        }}
        title={`Edit Policy: ${selectedPolicy?.name}`}
        size="md"
      >
        <form onSubmit={handleUpdatePolicy} className="space-y-4">
          <Input
            label="Description"
            placeholder="Describe this policy type"
            value={editPolicy.description || ''}
            onChange={(e) => setEditPolicy({ ...editPolicy, description: e.target.value })}
          />

          <Input
            label="Reset Days"
            type="number"
            placeholder="Leave empty for unlimited"
            value={editPolicy.resetDays || ''}
            onChange={(e) =>
              setEditPolicy({
                ...editPolicy,
                resetDays: parseInt(e.target.value) || undefined,
              })
            }
          />

          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-300">Enabled</span>
            <Toggle
              checked={editPolicy.enabled ?? true}
              onChange={(checked) => setEditPolicy({ ...editPolicy, enabled: checked })}
            />
          </div>

          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setShowEditModal(false);
                setSelectedPolicy(null);
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
    </div>
  );
}
