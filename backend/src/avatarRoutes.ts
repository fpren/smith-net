// backend/src/avatarRoutes.ts
//
// Profile avatar upload. Deliberately separate from mediaHandler's
// /api/media/upload: that route is unauthenticated and message-keyed (it
// requires messageId/channelId/senderId), so it must NOT be used for avatars —
// anyone could overwrite anyone's photo. This route is authenticateToken-gated
// and scopes every write to req.user.id.
//
// The handler writes the file to disk and persists avatar_url in one synchronous
// DB write, then returns. No deferred side effect, no LLM, no fire-and-forget —
// compliant with the CLAUDE.md route rules. If thumbnailing is ever wanted it
// must go through background_jobs, not inline here.

import { Router, Request, Response } from 'express';
import multer from 'multer';
import fs from 'fs';
import { v4 as uuidv4 } from 'uuid';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { pg, isPgEnabled } from './db';
import { IMAGES_DIR } from './mediaHandler';

// Strict raster-only whitelist. Critically excludes image/svg+xml: avatars are
// served INLINE same-origin from /media/images, so an SVG (which can carry
// <script>) would be stored XSS. The on-disk extension is derived from the
// validated mimetype here — NEVER from file.originalname — so an attacker can't
// smuggle a .html/.svg/.php extension into the static-served directory.
const ALLOWED_IMAGE_EXT: Record<string, string> = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
  'image/webp': '.webp',
  'image/gif': '.gif',
};

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, IMAGES_DIR),
  filename: (_req, file, cb) => {
    // fileFilter has already rejected anything not in the whitelist, so the
    // lookup is safe; fall back to .jpg defensively.
    const ext = ALLOWED_IMAGE_EXT[file.mimetype] ?? '.jpg';
    cb(null, `avatar-${uuidv4()}${ext}`);
  },
});

const rasterImageOnly = (_req: Request, file: Express.Multer.File, cb: multer.FileFilterCallback) => {
  cb(null, Object.prototype.hasOwnProperty.call(ALLOWED_IMAGE_EXT, file.mimetype));
};

const upload = multer({
  storage,
  fileFilter: rasterImageOnly,
  limits: { fileSize: 5 * 1024 * 1024 }, // 5MB — avatars, not message media
});

export const avatarRouter = Router();

avatarRouter.use(authenticateToken);

// POST /api/profile/avatar — multipart 'file'. Stores the image and points the
// caller's profile at it. Returns { avatarUrl }.
avatarRouter.post('/avatar', upload.single('file'), async (req: Request, res: Response) => {
  const userId = (req as AuthenticatedRequest).user!.id;
  if (!req.file) {
    return res.status(400).json({ error: 'No file uploaded' });
  }
  if (!isPgEnabled() || !pg) {
    fs.unlinkSync(req.file.path);
    return res.status(503).json({ error: 'directory unavailable' });
  }

  const avatarUrl = `/media/images/${req.file.filename}`;
  try {
    const { rowCount } = await pg.query(
      `UPDATE profiles SET avatar_url = $1 WHERE id = $2`,
      [avatarUrl, userId]
    );
    if (!rowCount) {
      fs.unlinkSync(req.file.path);
      return res.status(404).json({ error: 'profile not found' });
    }
    res.status(201).json({ avatarUrl });
  } catch (e: any) {
    fs.unlinkSync(req.file.path);
    console.error('[Avatar] upload error:', e.message);
    res.status(500).json({ error: 'Avatar upload failed' });
  }
});

// DELETE /api/profile/avatar — clear the avatar (revert to initials).
avatarRouter.delete('/avatar', async (req: Request, res: Response) => {
  const userId = (req as AuthenticatedRequest).user!.id;
  if (!isPgEnabled() || !pg) {
    return res.status(503).json({ error: 'directory unavailable' });
  }
  try {
    await pg.query(`UPDATE profiles SET avatar_url = NULL WHERE id = $1`, [userId]);
    res.status(204).send();
  } catch (e: any) {
    console.error('[Avatar] delete error:', e.message);
    res.status(500).json({ error: 'Avatar delete failed' });
  }
});

console.log('[Avatar] routes initialized');
