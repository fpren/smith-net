/**
 * F1.5: zod request-validation middleware.
 *
 * Apply directly to routes:
 *   router.post('/x', validateBody(MySchema), handler)
 *
 * On success, the parsed (coerced + stripped) data replaces req.body so
 * downstream handlers see the typed shape. On failure, returns a uniform
 * 400 envelope so clients can switch on `code: 'validation'` cleanly.
 *
 * Use a safe-parse error envelope rather than throwing, because Express's
 * default error handler returns a stack-trace HTML page in dev — useless
 * to API clients and a small info-leak.
 */

import { Request, Response, NextFunction } from 'express';
import { ZodType } from 'zod';

type ValidationTarget = 'body' | 'query' | 'params';

function makeValidator(target: ValidationTarget) {
  return <T extends ZodType>(schema: T) => {
    return (req: Request, res: Response, next: NextFunction) => {
      const result = schema.safeParse(req[target]);
      if (!result.success) {
        return res.status(400).json({
          error: 'Validation failed',
          code: 'validation',
          where: target,
          details: result.error.flatten(),
        });
      }
      // Replace with parsed data. zod has stripped unknown fields (in strict
      // mode they would have errored above) and coerced typed fields.
      // Note: req.query and req.params are getter-backed in Express 5; use
      // Object.defineProperty fallback only if the simple assignment fails.
      try {
        (req as unknown as Record<string, unknown>)[target] = result.data;
      } catch {
        Object.defineProperty(req, target, { value: result.data, writable: true });
      }
      next();
    };
  };
}

export const validateBody = makeValidator('body');
export const validateQuery = makeValidator('query');
export const validateParams = makeValidator('params');
