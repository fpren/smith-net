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
];
