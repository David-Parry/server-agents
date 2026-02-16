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
} from '@/components/ui';
import { FileText, Filter, Trash2, AlertTriangle, RefreshCw } from 'lucide-react';
import { format } from 'date-fns';

const EVENT_CATEGORIES = [
  { value: '', label: 'All Categories' },
  { value: 'TOKEN', label: 'Token' },
  { value: 'ADMIN', label: 'Admin' },
  { value: 'AUTHENTICATION', label: 'Authentication' },
  { value: 'ALLOWANCE', label: 'Allowance' },
  { value: 'CUSTOMER', label: 'Customer' },
];

const EVENT_TYPES = [
  { value: '', label: 'All Event Types' },
  { value: 'TOKEN_CREATED', label: 'Token Created' },
  { value: 'TOKEN_REVOKED', label: 'Token Revoked' },
  { value: 'TOKEN_EXPIRED', label: 'Token Expired' },
  { value: 'ADMIN_CUSTOMER_DISABLED', label: 'Customer Disabled' },
  { value: 'ADMIN_CUSTOMER_ENABLED', label: 'Customer Enabled' },
  { value: 'ADMIN_MODEL_DISABLED', label: 'Model Disabled' },
  { value: 'ADMIN_MODEL_ENABLED', label: 'Model Enabled' },
  { value: 'AUTHENTICATION_SUCCESS', label: 'Auth Success' },
  { value: 'AUTHENTICATION_FAILURE', label: 'Auth Failure' },
  { value: 'ALLOWANCE_UPDATED', label: 'Allowance Updated' },
  { value: 'ALLOWANCE_RESET', label: 'Allowance Reset' },
];

export default function AuditPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [showCleanupModal, setShowCleanupModal] = useState(false);
  const [cleanupDate, setCleanupDate] = useState('');

  // Filters
  const [filters, setFilters] = useState({
    customerId: '',
    eventType: '',
    eventCategory: '',
    from: '',
    to: '',
  });

  const { data: auditLogs, isLoading, refetch } = useQuery({
    queryKey: ['audit-logs', page, filters],
    queryFn: () =>
      api.queryAuditLogs({
        ...filters,
        customerId: filters.customerId || undefined,
        eventType: filters.eventType || undefined,
        eventCategory: filters.eventCategory || undefined,
        from: filters.from || undefined,
        to: filters.to || undefined,
        page,
        size: 50,
      }),
  });

  const { data: auditStats } = useQuery({
    queryKey: ['audit-stats'],
    queryFn: () => api.getAuditStatistics(),
  });

  const cleanupMutation = useMutation({
    mutationFn: (before: string) => api.cleanupAuditLogs(before),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['audit-logs'] });
      queryClient.invalidateQueries({ queryKey: ['audit-stats'] });
      setShowCleanupModal(false);
      alert(`Deleted ${response.deletedCount} audit logs`);
    },
  });

  const handleCleanup = () => {
    if (cleanupDate) {
      cleanupMutation.mutate(new Date(cleanupDate).toISOString());
    }
  };

  const getCategoryBadgeVariant = (category: string) => {
    switch (category) {
      case 'TOKEN':
        return 'info';
      case 'ADMIN':
        return 'warning';
      case 'AUTHENTICATION':
        return 'success';
      case 'ALLOWANCE':
        return 'default';
      default:
        return 'default';
    }
  };

  const getEventTypeBadgeVariant = (eventType: string) => {
    if (eventType.includes('FAILURE') || eventType.includes('DISABLED') || eventType.includes('REVOKED')) {
      return 'danger';
    }
    if (eventType.includes('SUCCESS') || eventType.includes('ENABLED') || eventType.includes('CREATED')) {
      return 'success';
    }
    return 'info';
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Audit Logs</h1>
          <p className="text-gray-400 mt-1">View and manage system audit trail</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={() => refetch()}>
            <RefreshCw className="w-4 h-4 mr-2" />
            Refresh
          </Button>
          <Button variant="danger" onClick={() => setShowCleanupModal(true)}>
            <Trash2 className="w-4 h-4 mr-2" />
            Cleanup
          </Button>
        </div>
      </div>

      {/* Stats */}
      {auditStats && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Card>
            <CardContent className="py-4">
              <p className="text-sm text-gray-400">Total Events (24h)</p>
              <p className="text-2xl font-semibold text-white">{auditStats.totalEvents}</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="py-4">
              <p className="text-sm text-gray-400">Event Types</p>
              <p className="text-2xl font-semibold text-white">
                {Object.keys(auditStats.eventCountsByType || {}).length}
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="py-4">
              <p className="text-sm text-gray-400">Since</p>
              <p className="text-lg font-semibold text-white">
                {auditStats.since
                  ? format(new Date(auditStats.since), 'MMM d, HH:mm')
                  : '-'}
              </p>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Filters */}
      <Card>
        <CardHeader>
          <h2 className="text-lg font-semibold text-white flex items-center gap-2">
            <Filter className="w-5 h-5 text-blue-400" />
            Filters
          </h2>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
            <Input
              placeholder="Customer ID"
              value={filters.customerId}
              onChange={(e) => setFilters({ ...filters, customerId: e.target.value })}
            />
            <Select
              options={EVENT_CATEGORIES}
              value={filters.eventCategory}
              onChange={(e) => setFilters({ ...filters, eventCategory: e.target.value })}
            />
            <Select
              options={EVENT_TYPES}
              value={filters.eventType}
              onChange={(e) => setFilters({ ...filters, eventType: e.target.value })}
            />
            <Input
              type="datetime-local"
              placeholder="From"
              value={filters.from}
              onChange={(e) => setFilters({ ...filters, from: e.target.value })}
            />
            <Input
              type="datetime-local"
              placeholder="To"
              value={filters.to}
              onChange={(e) => setFilters({ ...filters, to: e.target.value })}
            />
          </div>
          <div className="flex justify-end mt-4">
            <Button
              variant="secondary"
              size="sm"
              onClick={() =>
                setFilters({
                  customerId: '',
                  eventType: '',
                  eventCategory: '',
                  from: '',
                  to: '',
                })
              }
            >
              Clear Filters
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Audit Logs Table */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-white flex items-center gap-2">
              <FileText className="w-5 h-5 text-purple-400" />
              Audit Log Entries
            </h2>
            <span className="text-sm text-gray-400">
              {auditLogs?.totalElements ?? 0} total entries
            </span>
          </div>
        </CardHeader>
        {isLoading ? (
          <div className="flex justify-center py-12">
            <Spinner className="w-8 h-8" />
          </div>
        ) : auditLogs?.content && auditLogs.content.length > 0 ? (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Timestamp</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Event Type</TableHead>
                  <TableHead>Description</TableHead>
                  <TableHead>Actor</TableHead>
                  <TableHead>Customer</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {auditLogs.content.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell>
                      <span className="text-xs font-mono">
                        {format(new Date(log.createdAt), 'MMM d, HH:mm:ss')}
                      </span>
                    </TableCell>
                    <TableCell>
                      <Badge variant={getCategoryBadgeVariant(log.eventCategory)}>
                        {log.eventCategory}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={getEventTypeBadgeVariant(log.eventType)}>
                        {log.eventType.replace(/_/g, ' ')}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <p className="text-sm text-gray-300 max-w-md truncate">
                        {log.description}
                      </p>
                    </TableCell>
                    <TableCell>
                      <span className="text-xs text-gray-400">
                        {log.actorType}
                        {log.actorId && `: ${log.actorId.slice(0, 8)}...`}
                      </span>
                    </TableCell>
                    <TableCell>
                      {log.customerId ? (
                        <span className="text-xs font-mono text-gray-400">
                          {log.customerId.slice(0, 8)}...
                        </span>
                      ) : (
                        '-'
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {auditLogs.totalPages > 1 && (
              <Pagination
                currentPage={page}
                totalPages={auditLogs.totalPages}
                onPageChange={setPage}
              />
            )}
          </>
        ) : (
          <CardContent>
            <EmptyState
              icon={<FileText className="w-12 h-12" />}
              title="No audit logs found"
              description="Audit logs will appear here as system events occur"
            />
          </CardContent>
        )}
      </Card>

      {/* Cleanup Modal */}
      <Modal
        isOpen={showCleanupModal}
        onClose={() => setShowCleanupModal(false)}
        title="Cleanup Audit Logs"
        size="md"
      >
        <div className="space-y-4">
          <div className="p-4 bg-red-900/20 border border-red-700 rounded-lg flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-red-400 mt-0.5" />
            <div>
              <p className="text-sm text-red-400 font-medium">Warning: This action is irreversible</p>
              <p className="text-sm text-gray-400 mt-1">
                All audit logs before the selected date will be permanently deleted.
              </p>
            </div>
          </div>

          <Input
            label="Delete logs before"
            type="datetime-local"
            value={cleanupDate}
            onChange={(e) => setCleanupDate(e.target.value)}
          />

          <div className="flex justify-end gap-3 pt-4">
            <Button variant="secondary" onClick={() => setShowCleanupModal(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={handleCleanup}
              loading={cleanupMutation.isPending}
              disabled={!cleanupDate}
            >
              Delete Logs
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
