'use client';

import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient, useQueries } from '@tanstack/react-query';
import { api } from '@/lib/api';
import {
  Card,
  CardContent,
  CardHeader,
  Button,
  Input,
  Select,
  Modal,
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  Badge,
  Spinner,
  Pagination,
  EmptyState,
  Toggle,
} from '@/components/ui';
import { Plus, Users, Eye, Key, Trash2, RefreshCw, Copy, Check, Wifi, WifiOff } from 'lucide-react';
import { format } from 'date-fns';
import Link from 'next/link';
import { CreateCustomerRequest, CustomerConnectionStatusResponse } from '@/lib/types';

function useCustomerConnection(customerId: string | undefined) {
  return useQuery({
    queryKey: ['customer-connection', customerId],
    queryFn: () => api.getCustomerConnectionStatus(customerId as string),
    enabled: !!customerId,
    staleTime: 5000,
    refetchInterval: 10000,
  });
}

function CustomerRow({ 
  customer, 
  onGenerateToken, 
  onDisable, 
  onEnable 
}: { 
  customer: any; 
  onGenerateToken: (id: string) => void;
  onDisable: (id: string) => void;
  onEnable: (id: string) => void;
}) {
  const { data: conn, isLoading: connLoading } = useCustomerConnection(customer.customerId);
  
  return (
    <TableRow>
      <TableCell>
        <div>
          <p className="font-medium text-white">{customer.name}</p>
          <p className="text-xs text-gray-500 font-mono">
            {customer.customerId}
          </p>
        </div>
      </TableCell>
      <TableCell>
        <Badge variant={customer.enabled ? 'success' : 'danger'}>
          {customer.enabled ? 'Enabled' : 'Disabled'}
        </Badge>
      </TableCell>
      <TableCell>
        {connLoading ? (
          <Spinner className="w-4 h-4" />
        ) : conn?.connected ? (
          <div className="flex items-center gap-1 text-green-400" title="WebSocket Connected">
            <Wifi className="w-4 h-4" />
            <span className="text-xs">Online</span>
          </div>
        ) : (
          <div className="flex items-center gap-1 text-gray-400" title="No Active WebSocket">
            <WifiOff className="w-4 h-4" />
            <span className="text-xs">Offline</span>
          </div>
        )}
      </TableCell>
      <TableCell>{customer.activeTokenCount}</TableCell>
      <TableCell>{customer.modelAllowanceCount}</TableCell>
      <TableCell>
        {format(new Date(customer.createdAt), 'MMM d, yyyy')}
      </TableCell>
      <TableCell>
        <div className="flex items-center gap-2">
          <Link href={`/customers/${customer.customerId}`}>
            <Button variant="ghost" size="sm" title="View Details">
              <Eye className="w-4 h-4" />
            </Button>
          </Link>
          {customer.enabled ? (
            <Button
              variant="ghost"
              size="sm"
              title="Disable Customer"
              onClick={() => onDisable(customer.customerId)}
            >
              <Trash2 className="w-4 h-4 text-red-400" />
            </Button>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              title="Enable Customer"
              onClick={() => onEnable(customer.customerId)}
            >
              <RefreshCw className="w-4 h-4 text-green-400" />
            </Button>
          )}
        </div>
      </TableCell>
    </TableRow>
  );
}

export default function CustomersPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [enabledFilter, setEnabledFilter] = useState<boolean | undefined>(undefined);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showTokenModal, setShowTokenModal] = useState(false);
  const [selectedCustomerId, setSelectedCustomerId] = useState<string | null>(null);
  const [generatedToken, setGeneratedToken] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // Form state
  const [newCustomer, setNewCustomer] = useState<CreateCustomerRequest>({
    name: '',
    policyTypeName: 'UNLIMITED',
    unlimited: true,
  });

  const { data: customers, isLoading } = useQuery({
    queryKey: ['customers', page, enabledFilter],
    queryFn: () => api.listCustomers(page, 20, enabledFilter),
  });

  const { data: policyTypes } = useQuery({
    queryKey: ['policy-types'],
    queryFn: () => api.listPolicyTypes(),
  });

  const createMutation = useMutation({
    mutationFn: (data: CreateCustomerRequest) => api.createCustomer(data),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setShowCreateModal(false);
      setGeneratedToken(response.apiToken);
      setShowTokenModal(true);
      setNewCustomer({ name: '', policyTypeName: 'UNLIMITED', unlimited: true });
    },
  });

  const generateTokenMutation = useMutation({
    mutationFn: (customerId: string) => api.generateToken(customerId),
    onSuccess: (response) => {
      setGeneratedToken(response.apiToken);
      setShowTokenModal(true);
    },
  });

  const disableMutation = useMutation({
    mutationFn: (customerId: string) => api.disableCustomer(customerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
  });

  const enableMutation = useMutation({
    mutationFn: (customerId: string) => api.enableCustomer(customerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
  });

  const handleCopyToken = () => {
    if (generatedToken) {
      navigator.clipboard.writeText(generatedToken);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleCreateCustomer = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate(newCustomer);
  };

  const handleGenerateToken = (customerId: string) => {
    setSelectedCustomerId(customerId);
    generateTokenMutation.mutate(customerId);
  };

  const handleDisable = (customerId: string) => {
    disableMutation.mutate(customerId);
  };

  const handleEnable = (customerId: string) => {
    enableMutation.mutate(customerId);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Customers</h1>
          <p className="text-gray-400 mt-1">Manage customer accounts and API tokens</p>
        </div>
        <Button onClick={() => setShowCreateModal(true)}>
          <Plus className="w-4 h-4 mr-2" />
          Add Customer
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="py-3">
          <div className="flex items-center gap-4">
            <Select
              options={[
                { value: '', label: 'All Customers' },
                { value: 'true', label: 'Enabled Only' },
                { value: 'false', label: 'Disabled Only' },
              ]}
              value={enabledFilter === undefined ? '' : String(enabledFilter)}
              onChange={(e) =>
                setEnabledFilter(e.target.value === '' ? undefined : e.target.value === 'true')
              }
              className="w-48"
            />
            <span className="text-sm text-gray-400">
              {customers?.totalElements ?? 0} total customers
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Customers Table */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white flex items-center gap-2">
            <Users className="w-5 h-5 text-blue-400" />
            Customer List
          </h2>
        </CardHeader>
        {isLoading ? (
          <div className="flex justify-center py-12">
            <Spinner className="w-8 h-8" />
          </div>
        ) : customers?.content && customers.content.length > 0 ? (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>WS</TableHead>
                  <TableHead>Active Tokens</TableHead>
                  <TableHead>Model Allowances</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {customers.content.map((customer) => (
                  <CustomerRow
                    key={customer.id}
                    customer={customer}
                    onGenerateToken={handleGenerateToken}
                    onDisable={handleDisable}
                    onEnable={handleEnable}
                  />
                ))}
              </TableBody>
            </Table>
            {customers.totalPages > 1 && (
              <Pagination
                currentPage={page}
                totalPages={customers.totalPages}
                onPageChange={setPage}
              />
            )}
          </>
        ) : (
          <CardContent>
            <EmptyState
              icon={<Users className="w-12 h-12" />}
              title="No customers found"
              description="Get started by creating your first customer"
              action={
                <Button onClick={() => setShowCreateModal(true)}>
                  <Plus className="w-4 h-4 mr-2" />
                  Add Customer
                </Button>
              }
            />
          </CardContent>
        )}
      </Card>

      {/* Create Customer Modal */}
      <Modal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Create New Customer"
        size="md"
      >
        <form onSubmit={handleCreateCustomer} className="space-y-4">
          <Input
            label="Customer Name"
            placeholder="Enter customer name"
            value={newCustomer.name}
            onChange={(e) => setNewCustomer({ ...newCustomer, name: e.target.value })}
            required
          />

          <Select
            label="Policy Type"
            options={
              policyTypes?.filter((p) => p.enabled).map((p) => ({
                value: p.name,
                label: `${p.name}${p.resetDays ? ` (${p.resetDays} days)` : ' (Unlimited)'}`,
              })) ?? [{ value: 'UNLIMITED', label: 'UNLIMITED' }]
            }
            value={newCustomer.policyTypeName || 'UNLIMITED'}
            onChange={(e) =>
              setNewCustomer({ ...newCustomer, policyTypeName: e.target.value })
            }
          />

          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-300">Unlimited Tokens</span>
            <Toggle
              checked={newCustomer.unlimited ?? true}
              onChange={(checked) => setNewCustomer({ ...newCustomer, unlimited: checked })}
            />
          </div>

          {!newCustomer.unlimited && (
            <Input
              label="Default Token Allowance"
              type="number"
              placeholder="Enter token allowance"
              value={newCustomer.defaultAllowance || ''}
              onChange={(e) =>
                setNewCustomer({
                  ...newCustomer,
                  defaultAllowance: parseInt(e.target.value) || undefined,
                })
              }
            />
          )}

          <div className="flex justify-end gap-3 pt-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setShowCreateModal(false)}
            >
              Cancel
            </Button>
            <Button type="submit" loading={createMutation.isPending}>
              Create Customer
            </Button>
          </div>
        </form>
      </Modal>

      {/* Token Display Modal */}
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
            <Button
              variant="ghost"
              size="sm"
              className="absolute top-2 right-2"
              onClick={handleCopyToken}
            >
              {copied ? (
                <Check className="w-4 h-4 text-green-400" />
              ) : (
                <Copy className="w-4 h-4" />
              )}
            </Button>
          </div>

          <div className="flex justify-end">
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
    </div>
  );
}
