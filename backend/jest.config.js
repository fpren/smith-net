/** @type {import('jest').Config} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  testMatch: ['<rootDir>/src/__tests__/**/*.test.ts'],
  // Tests share an in-memory userStore — run serially to avoid interference.
  maxWorkers: 1,
  // Keep open-handles errors visible (the auth code uses timers).
  detectOpenHandles: true,
};
