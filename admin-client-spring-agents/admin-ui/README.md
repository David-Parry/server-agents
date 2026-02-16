# Spring Agents Admin Panel

A Next.js admin application for managing the Spring Agents MCP Proxy service.

## Features

- **Dashboard**: Overview of system statistics, recent activity, and quick actions
- **Customer Management**: Create, view, enable/disable customers, manage API tokens
- **Model Management**: Add, configure, and manage LLM models
- **Policy Types**: Configure token reset policies (daily, weekly, monthly, unlimited)
- **Audit Logs**: View and filter system audit trail with cleanup capabilities
- **Settings**: Configure admin token and API settings

## Authentication

This admin panel uses **Bearer Token** authentication. You'll need the admin token configured on the server via the `AGENT_ADMIN_API_TOKEN` environment variable.

When you first access the admin panel, you'll be prompted to enter your admin token. The token is stored in localStorage and sent with all API requests as:

```
Authorization: Bearer <admin-token>
```

## Getting Started

### Prerequisites

- Node.js 18+ 
- npm or yarn
- Spring Agents API server running (default: http://localhost:8080)

### Installation

```bash
# Install dependencies
npm install

# Run development server
npm run dev
```

The admin panel will be available at [http://localhost:3000](http://localhost:3000).

### Production Build

```bash
# Build for production
npm run build

# Start production server
npm start
```

## Configuration

### API Proxy

During development, API requests are proxied to `http://localhost:8080`. This is configured in `next.config.ts`:

```typescript
async rewrites() {
  return [
    {
      source: '/api/:path*',
      destination: 'http://localhost:8080/api/:path*',
    },
  ];
}
```

### Custom API URL

If your API is hosted on a different server, you can configure the API URL in the Settings page. The URL is stored in localStorage and used for all API requests.

## Project Structure

```
src/
├── app/                    # Next.js App Router pages
│   ├── audit/             # Audit logs page
│   ├── customers/         # Customer management
│   │   └── [customerId]/  # Customer detail page
│   ├── dashboard/         # Dashboard page
│   ├── models/            # Model management
│   ├── policies/          # Policy types management
│   ├── settings/          # Settings page
│   ├── globals.css        # Global styles
│   ├── layout.tsx         # Root layout
│   ├── page.tsx           # Home (redirects to dashboard)
│   └── providers.tsx      # Context providers
├── components/            # Reusable UI components
│   ├── layout.tsx         # Main layout with sidebar
│   ├── login-form.tsx     # Login form component
│   ├── sidebar.tsx        # Navigation sidebar
│   └── ui.tsx             # UI component library
└── lib/                   # Utilities and API client
    ├── api.ts             # API client with all endpoints
    ├── auth-context.tsx   # Authentication context
    ├── query-provider.tsx # React Query provider
    └── types.ts           # TypeScript types from OpenAPI
```

## API Endpoints Used

The admin panel uses the following API endpoints:

### Admin Endpoints (require Bearer token)
- `GET /api/admin/stats` - System statistics
- `GET /api/admin/customers` - List customers (paginated)
- `GET /api/admin/customers/{id}` - Customer details
- `DELETE /api/admin/customers/{id}` - Disable customer
- `POST /api/admin/customers/{id}/enable` - Enable customer
- `GET /api/admin/models` - List all models
- `PUT /api/admin/models/{model}` - Update model
- `POST /api/admin/models/{model}/enable` - Enable model
- `DELETE /api/admin/models/{model}` - Disable model
- `GET /api/admin/policy-types` - List policy types
- `POST /api/admin/policy-types` - Create policy type
- `PUT /api/admin/policy-types/{id}` - Update policy type
- `DELETE /api/admin/policy-types/{id}` - Disable policy type
- `GET /api/admin/audit` - Query audit logs
- `GET /api/admin/audit/stats` - Audit statistics
- `DELETE /api/admin/audit/cleanup` - Cleanup old logs
- `GET /api/admin/allowances/by-model/{model}` - Allowances by model
- `PUT /api/admin/allowances/bulk` - Bulk update allowances
- `POST /api/admin/allowances/reset-all/{model}` - Reset usage for model

### Customer Endpoints
- `POST /api/customers` - Create customer
- `POST /api/customers/{id}/tokens` - Generate token
- `DELETE /api/customers/{id}/tokens` - Revoke all tokens
- `GET /api/customers/{id}/allowances` - Get allowances
- `PUT /api/customers/{id}/allowances/{model}` - Set allowance
- `POST /api/customers/{id}/allowances/{model}/reset` - Reset usage
- `POST /api/customers/{id}/allowances/reset-all` - Reset all usage
- `PATCH /api/customers/{id}/notification-settings` - Update notifications

### Model Endpoints
- `POST /api/models` - Create model
- `POST /api/models/{model}/link-all-customers` - Link to all customers

## Tech Stack

- **Next.js 15** - React framework with App Router
- **TypeScript** - Type safety
- **Tailwind CSS** - Styling
- **React Query** - Data fetching and caching
- **Lucide React** - Icons
- **date-fns** - Date formatting

## License

Apache 2.0
