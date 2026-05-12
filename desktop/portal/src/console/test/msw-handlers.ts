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
          engagementId: null,
          title: 'Test Job',
          description: null,
          status: 'planned',
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
        engagementId: null,
        title: 'Detail Job',
        description: null,
        status: 'planned',
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
          engagementId: null,
          title: body.title,
          description: null,
          status: 'planned',
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
        engagementId: null,
        title: body.title ?? 'Detail Job',
        description: body.description ?? null,
        status: 'planned',
        scheduledAt: body.scheduledAt ?? null,
        location: body.location ?? null,
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T11:00:00Z',
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
        engagementId: null,
        title: 'Detail Job',
        description: null,
        status: body.status,
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
];
