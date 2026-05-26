import { http, HttpResponse } from 'msw';

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    if (body.email === 'foreman@example.com' && body.password === 'password123') {
      return HttpResponse.json(
        {
          user: {
            id: 'user-1',
            email: 'foreman@example.com',
            displayName: 'Test Foreman',
            role: 'foreman',
            permissions: [],
            emailVerified: true,
          },
          accessToken: 'fake-access',
          refreshToken: 'fake-refresh',
          expiresIn: 604800,
        },
        {
          headers: {
            'set-cookie': 'smithnet_access=fake-access; HttpOnly; SameSite=Strict; Path=/api',
          },
        }
      );
    }
    return HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 });
  }),

  http.get('/api/auth/me', () => {
    return HttpResponse.json({
      user: {
        id: 'user-1',
        email: 'foreman@example.com',
        displayName: 'Test Foreman',
        role: 'foreman',
        permissions: [],
        emailVerified: true,
      },
    });
  }),

  http.post('/api/auth/refresh', () => {
    return HttpResponse.json({
      accessToken: 'fake-access-2',
      refreshToken: 'fake-refresh-2',
      expiresIn: 604800,
    });
  }),

  http.post('/api/auth/logout', () => {
    return HttpResponse.json({ success: true });
  }),

  http.post('/api/auth/register', async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string; displayName: string };
    return HttpResponse.json(
      {
        user: {
          id: 'user-new',
          email: body.email,
          displayName: body.displayName,
          role: 'solo',
          permissions: [],
          emailVerified: false,
        },
        accessToken: 'fake-access',
        refreshToken: 'fake-refresh',
        expiresIn: 604800,
        requiresEmailVerification: true,
      },
      { status: 201 }
    );
  }),

  http.get('/api/jobs', () => {
    return HttpResponse.json({
      jobs: [
        {
          id: 'job-1',
          foremanId: 'user-1',
          clientId: null,
          client: null,
          engagementId: null,
          title: 'Test Job',
          description: null,
          status: 'planned',
          stage: 'lead',
          scheduledAt: null,
          location: 'Test Location',
          createdAt: '2026-05-11T10:00:00Z',
          updatedAt: '2026-05-11T10:00:00Z',
        },
      ],
    });
  }),

  http.get('/api/jobs/:id', ({ params }) => {
    return HttpResponse.json({
      job: {
        id: params.id,
        foremanId: 'user-1',
        clientId: null,
        client: null,
        engagementId: null,
        title: 'Detail Job',
        description: null,
        status: 'planned',
        stage: 'lead',
        scheduledAt: null,
        location: 'Detail Location',
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T10:00:00Z',
      },
      crew: [],
    });
  }),

  http.post('/api/jobs', async ({ request }) => {
    const body = (await request.json()) as { title: string; location?: string };
    return HttpResponse.json(
      {
        job: {
          id: 'new-job-id',
          foremanId: 'user-1',
          clientId: null,
          client: null,
          engagementId: null,
          title: body.title,
          description: null,
          status: 'planned',
          stage: 'lead',
          scheduledAt: null,
          location: body.location ?? null,
          createdAt: '2026-05-11T10:00:00Z',
          updatedAt: '2026-05-11T10:00:00Z',
        },
      },
      { status: 201 }
    );
  }),

  http.patch('/api/jobs/:id', async ({ params, request }) => {
    const body = (await request.json()) as Record<string, any>;
    return HttpResponse.json({
      job: {
        id: params.id,
        foremanId: 'user-1',
        clientId: null,
        client: null,
        engagementId: null,
        title: body.title ?? 'Detail Job',
        description: body.description ?? null,
        status: 'planned',
        stage: 'lead',
        scheduledAt: body.scheduledAt ?? null,
        location: body.location ?? null,
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T11:00:00Z',
      },
    });
  }),

  http.patch('/api/jobs/:id/stage', async ({ params, request }) => {
    const body = (await request.json()) as { stage: string };
    return HttpResponse.json({
      job: {
        id: params.id, foremanId: 'f-1', clientId: null, engagementId: null,
        title: 'Mock Job', description: null, status: 'planned', stage: body.stage,
        scheduledAt: null, location: null, latitude: null, longitude: null,
        geocodedAt: null, createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T11:00:00Z', client: null,
      },
    });
  }),

  http.patch('/api/jobs/:id/status', async ({ params, request }) => {
    const body = (await request.json()) as { status: string };
    return HttpResponse.json({
      job: {
        id: params.id,
        foremanId: 'user-1',
        clientId: null,
        client: null,
        engagementId: null,
        title: 'Detail Job',
        description: null,
        status: body.status,
        stage: 'lead',
        scheduledAt: null,
        location: null,
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T11:00:00Z',
      },
    });
  }),

  http.post('/api/jobs/:id/assign', async ({ params, request }) => {
    const body = (await request.json()) as { profileId: string; roleOnJob?: string };
    return HttpResponse.json(
      {
        assignment: {
          jobId: params.id,
          profileId: body.profileId,
          roleOnJob: body.roleOnJob ?? 'crew',
          assignedAt: '2026-05-11T11:00:00Z',
        },
      },
      { status: 201 }
    );
  }),

  http.delete('/api/jobs/:id/assign/:profileId', () => {
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('/api/profiles', ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q') || '';
    if (q.length < 2) {
      return HttpResponse.json(
        { error: 'Validation failed', code: 'validation', details: {} },
        { status: 400 }
      );
    }
    return HttpResponse.json({
      profiles: [
        { id: 'p-1', email: 'alice@example.com', displayName: 'Alice', role: 'team' },
        { id: 'p-2', email: 'bob@example.com', displayName: 'Bob', role: 'lead' },
      ].filter((p) => p.email.includes(q) || p.displayName.toLowerCase().includes(q.toLowerCase())),
    });
  }),

  http.get('/api/profiles/crew', () => {
    return HttpResponse.json({
      crew: [
        {
          id: 'p-1',
          email: 'alice@example.com',
          displayName: 'Alice',
          role: 'team',
          activeJob: { id: 'j-1', title: 'Maple Ave', status: 'in_progress' },
        },
        {
          id: 'p-2',
          email: 'bob@example.com',
          displayName: 'Bob',
          role: 'lead',
          activeJob: null,
        },
      ],
    });
  }),

  http.get('/api/crew/positions', () => {
    return HttpResponse.json({ positions: [] });
  }),

  http.post('/api/shifts/start', () =>
    HttpResponse.json({
      shift: { id: 'shift-msw', userId: 'user-1', startedAt: '2026-05-16T00:00:00Z', endedAt: null, source: 'web' },
    })
  ),

  http.post('/api/shifts/end', () =>
    HttpResponse.json({
      shift: { id: 'shift-msw', userId: 'user-1', startedAt: '2026-05-16T00:00:00Z', endedAt: '2026-05-16T01:00:00Z', source: 'web' },
    })
  ),

  http.get('/api/shifts/current', () => HttpResponse.json({ shift: null })),

  http.get('/api/shifts/today', () => HttpResponse.json({ shifts: [] })),

  http.post('/api/presence/location', () =>
    HttpResponse.json({
      position: {
        userId: 'user-1', latitude: 40.7, longitude: -74,
        accuracyM: 5, recordedAt: '2026-05-16T00:01:00Z', source: 'web', batteryPct: 80,
      },
    })
  ),

  // Invoices (migration 017 + invoicesRoutes.ts).
  http.get('/api/invoices', () =>
    HttpResponse.json({
      invoices: [
        {
          id: 'inv-1',
          organizationId: 'org-1',
          createdBy: 'user-1',
          invoiceNumber: 'INV-2026-0001',
          clientName: 'Acme Roofing',
          clientEmail: 'ops@acme.com',
          issueDate: '2026-05-11T10:00:00Z',
          dueDate: null,
          status: 'draft',
          subtotal: 0,
          taxRate: 0,
          taxAmount: 0,
          totalDue: 0,
          notes: null,
          createdAt: '2026-05-11T10:00:00Z',
          updatedAt: '2026-05-11T10:00:00Z',
        },
      ],
    }),
  ),

  http.post('/api/invoices', async ({ request }) => {
    const body = (await request.json()) as { clientName?: string; clientEmail?: string };
    return HttpResponse.json(
      {
        invoice: {
          id: 'inv-new',
          organizationId: 'org-1',
          createdBy: 'user-1',
          invoiceNumber: 'INV-2026-0002',
          clientName: body.clientName ?? null,
          clientEmail: body.clientEmail ?? null,
          issueDate: '2026-05-11T11:00:00Z',
          dueDate: null,
          status: 'draft',
          subtotal: 0,
          taxRate: 0,
          taxAmount: 0,
          totalDue: 0,
          notes: null,
          createdAt: '2026-05-11T11:00:00Z',
          updatedAt: '2026-05-11T11:00:00Z',
        },
      },
      { status: 201 },
    );
  }),

  http.get('/api/invoices/:id', ({ params }) =>
    HttpResponse.json({
      invoice: {
        id: params.id,
        organizationId: 'org-1',
        createdBy: 'user-1',
        invoiceNumber: 'INV-2026-0001',
        clientName: 'Acme Roofing',
        clientEmail: 'ops@acme.com',
        issueDate: '2026-05-11T10:00:00Z',
        dueDate: null,
        status: 'draft',
        subtotal: 0,
        taxRate: 0,
        taxAmount: 0,
        totalDue: 0,
        notes: null,
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T10:00:00Z',
      },
      lineItems: [],
    }),
  ),

  http.patch('/api/invoices/:id', async ({ params, request }) => {
    const body = (await request.json()) as any;
    return HttpResponse.json({
      invoice: {
        id: params.id,
        organizationId: 'org-1',
        createdBy: 'user-1',
        invoiceNumber: 'INV-2026-0001',
        clientName: body.clientName ?? 'Acme Roofing',
        clientEmail: body.clientEmail ?? null,
        issueDate: '2026-05-11T10:00:00Z',
        dueDate: body.dueDate ?? null,
        status: 'draft',
        subtotal: 0,
        taxRate: body.taxRate ?? 0,
        taxAmount: 0,
        totalDue: 0,
        notes: body.notes ?? null,
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T12:00:00Z',
      },
    });
  }),

  http.patch('/api/invoices/:id/status', async ({ params, request }) => {
    const body = (await request.json()) as { status: string };
    return HttpResponse.json({
      invoice: {
        id: params.id,
        organizationId: 'org-1',
        createdBy: 'user-1',
        invoiceNumber: 'INV-2026-0001',
        clientName: 'Acme Roofing',
        clientEmail: 'ops@acme.com',
        issueDate: '2026-05-11T10:00:00Z',
        dueDate: null,
        status: body.status,
        subtotal: 0,
        taxRate: 0,
        taxAmount: 0,
        totalDue: 0,
        notes: null,
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T13:00:00Z',
      },
    });
  }),

  http.delete('/api/invoices/:id', () => new HttpResponse(null, { status: 204 })),

  http.post('/api/invoices/:id/line-items', async ({ params, request }) => {
    const body = (await request.json()) as { description: string; quantity?: number; unit?: string; rate: number; category?: string };
    const qty = body.quantity ?? 1;
    return HttpResponse.json(
      {
        lineItem: {
          id: 'li-new',
          invoiceId: params.id,
          description: body.description,
          quantity: qty,
          unit: body.unit ?? 'ea',
          rate: body.rate,
          total: qty * body.rate,
          category: body.category ?? 'other',
          sortOrder: 0,
          createdAt: '2026-05-11T11:30:00Z',
        },
      },
      { status: 201 },
    );
  }),

  http.patch('/api/line-items/:itemId', async ({ params, request }) => {
    const body = (await request.json()) as any;
    return HttpResponse.json({
      lineItem: {
        id: params.itemId,
        invoiceId: 'inv-1',
        description: body.description ?? 'Edited',
        quantity: body.quantity ?? 1,
        unit: body.unit ?? 'ea',
        rate: body.rate ?? 10,
        total: (body.quantity ?? 1) * (body.rate ?? 10),
        category: body.category ?? 'other',
        sortOrder: body.sortOrder ?? 0,
        createdAt: '2026-05-11T11:30:00Z',
      },
    });
  }),

  http.delete('/api/line-items/:itemId', () => new HttpResponse(null, { status: 204 })),

  // Per-job tasks (migration 016 + tasksRoutes.ts).
  http.get('/api/jobs/:jobId/tasks', ({ params }) =>
    HttpResponse.json({
      tasks: [
        {
          id: 't-1',
          jobId: params.jobId,
          title: 'First task',
          status: 'pending',
          sortOrder: 0,
          createdBy: 'user-1',
          createdAt: '2026-05-11T10:00:00Z',
          updatedAt: '2026-05-11T10:00:00Z',
          completedAt: null,
        },
      ],
    }),
  ),

  http.post('/api/tasks', async ({ request }) => {
    const body = (await request.json()) as { jobId: string; title: string };
    return HttpResponse.json(
      {
        task: {
          id: 't-new',
          jobId: body.jobId,
          title: body.title,
          status: 'pending',
          sortOrder: 1,
          createdBy: 'user-1',
          createdAt: '2026-05-11T11:00:00Z',
          updatedAt: '2026-05-11T11:00:00Z',
          completedAt: null,
        },
      },
      { status: 201 },
    );
  }),

  http.patch('/api/tasks/:id', async ({ params, request }) => {
    const body = (await request.json()) as { title?: string; status?: 'pending' | 'done' };
    return HttpResponse.json({
      task: {
        id: params.id,
        jobId: 'job-1',
        title: body.title ?? 'First task',
        status: body.status ?? 'pending',
        sortOrder: 0,
        createdBy: 'user-1',
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T11:00:00Z',
        completedAt: body.status === 'done' ? '2026-05-11T11:00:00Z' : null,
      },
    });
  }),

  http.delete('/api/tasks/:id', () => new HttpResponse(null, { status: 204 })),

  // Comm — channels list + messages history + send.
  http.get('/api/channels', () =>
    HttpResponse.json([
      {
        id: 'ch-general',
        name: 'general',
        type: 'group',
        visibility: 'public',
        creatorId: 'user-1',
        createdAt: 1716000000000,
        memberIds: ['user-1'],
        allowedUsers: [],
        blockedUsers: [],
        pendingRequests: [],
        requiresApproval: false,
        isArchived: false,
        isDeleted: false,
      },
    ])
  ),

  http.get('/api/channels/:id/messages', ({ params }) =>
    HttpResponse.json([
      {
        id: 'msg-1',
        channelId: params.id,
        senderId: 'user-1',
        senderName: 'Test Foreman',
        content: 'hello world',
        timestamp: 1716000001000,
        origin: 'online',
      },
    ])
  ),

  http.delete('/api/messages/:id', () => new HttpResponse(null, { status: 204 })),

  http.post('/api/messages/inject', async ({ request }) => {
    const body = (await request.json()) as { channelId: string; content: string };
    return HttpResponse.json(
      {
        id: 'msg-new',
        channelId: body.channelId,
        senderId: 'user-1',
        senderName: 'Test Foreman',
        content: body.content,
        timestamp: 1716000099000,
        origin: 'online',
        meshInjected: false,
        relayCount: 0,
      },
      { status: 201 }
    );
  }),

  // Phase 4 admin health endpoint.
  http.get('/api/admin/health', () =>
    HttpResponse.json({
      workers: [
        {
          workerId: '12345@host',
          kinds: ['geocode', 'audit_flush', 'email'],
          lastBeatAt: '2026-05-16T00:00:00Z',
          ageSec: 7,
        },
      ],
      queue: {
        byKindState: [
          { kind: 'geocode', state: 'succeeded', count: 12 },
          { kind: 'audit_flush', state: 'queued', count: 1 },
        ],
        oldestQueued: { kind: 'audit_flush', scheduledAt: '2026-05-16T00:00:00Z', ageSec: 3 },
        oldestRunning: null,
      },
    })
  ),

  // Clients (clientsRoutes.ts).
  http.get('/api/clients', () =>
    HttpResponse.json({ clients: [
      { id: 'client-1', ownerId: 'f-1', name: 'Test Client', email: 't@c.test',
        phone: null, address: null, company: null, notes: null,
        createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z' },
    ] })),
  http.post('/api/clients', async ({ request }) => {
    const body = (await request.json()) as { name: string };
    return HttpResponse.json({ client: {
      id: 'new-client-id', ownerId: 'f-1', name: body.name, email: null,
      phone: null, address: null, company: null, notes: null,
      createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z' },
    }, { status: 201 });
  }),
  http.get('/api/clients/:id', ({ params }) =>
    HttpResponse.json({
      client: { id: params.id, ownerId: 'f-1', name: 'Test Client', email: 't@c.test',
        phone: null, address: null, company: null, notes: null,
        createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z' },
      jobs: [],
    })),
  http.patch('/api/clients/:id', async ({ request, params }) => {
    const body = (await request.json()) as Record<string, unknown>;
    return HttpResponse.json({ client: {
      id: params.id, ownerId: 'f-1',
      name: (body.name as string) ?? 'Test Client',
      email: (body.email as string) ?? null,
      phone: (body.phone as string) ?? null,
      address: (body.address as string) ?? null,
      company: (body.company as string) ?? null,
      notes: (body.notes as string) ?? null,
      createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z' },
    });
  }),
  http.delete('/api/clients/:id', () => new HttpResponse(null, { status: 204 })),
];
