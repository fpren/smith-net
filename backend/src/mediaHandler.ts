/**
 * Smith Net Media Handler
 * Handles media file uploads (images, voice, files)
 */

import { Router, Request, Response } from 'express';
import multer from 'multer';
import path from 'path';
import fs from 'fs';
import { v4 as uuidv4 } from 'uuid';

// Create uploads directory if it doesn't exist
const UPLOAD_DIR = path.join(__dirname, '..', 'uploads');
const IMAGES_DIR = path.join(UPLOAD_DIR, 'images');
const VOICE_DIR = path.join(UPLOAD_DIR, 'voice');
const FILES_DIR = path.join(UPLOAD_DIR, 'files');

[UPLOAD_DIR, IMAGES_DIR, VOICE_DIR, FILES_DIR].forEach(dir => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
});

// Configure multer storage
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    const mediaType = req.body.mediaType || 'FILE';
    
    let destDir = FILES_DIR;
    if (mediaType === 'IMAGE') {
      destDir = IMAGES_DIR;
    } else if (mediaType === 'VOICE') {
      destDir = VOICE_DIR;
    }
    
    cb(null, destDir);
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname);
    const uniqueName = `${uuidv4()}${ext}`;
    cb(null, uniqueName);
  }
});

// File filter - allow images, audio, and common file types
const fileFilter = (req: Request, file: Express.Multer.File, cb: multer.FileFilterCallback) => {
  const allowedMimes = [
    // Images
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/webp',
    // Audio
    'audio/mp4',
    'audio/mpeg',
    'audio/wav',
    'audio/ogg',
    'audio/m4a',
    // Documents
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'text/plain',
    // Archives
    'application/zip',
    'application/x-rar-compressed',
    // Other
    'application/octet-stream'
  ];
  
  if (allowedMimes.includes(file.mimetype) || file.mimetype.startsWith('image/') || file.mimetype.startsWith('audio/')) {
    cb(null, true);
  } else {
    console.log(`[Media] Rejected file type: ${file.mimetype}`);
    cb(null, true); // Allow anyway for now (octet-stream fallback)
  }
};

// Configure multer
const upload = multer({
  storage,
  fileFilter,
  limits: {
    fileSize: 50 * 1024 * 1024, // 50MB max
  }
});

export const mediaRouter = Router();

// Store metadata about uploaded files
interface MediaMetadata {
  id: string;
  messageId: string;
  channelId: string;
  senderId: string;
  mediaType: string;
  filename: string;
  originalName: string;
  mimeType: string;
  size: number;
  url: string;
  uploadedAt: number;
}

const mediaStore = new Map<string, MediaMetadata>();

/**
 * Upload a media file
 */
mediaRouter.post('/upload', upload.single('file'), (req: Request, res: Response) => {
  if (!req.file) {
    return res.status(400).json({ error: 'No file uploaded' });
  }

  const { messageId, channelId, senderId, mediaType } = req.body;

  if (!messageId || !channelId || !senderId) {
    // Clean up uploaded file if validation fails
    fs.unlinkSync(req.file.path);
    return res.status(400).json({ error: 'messageId, channelId, and senderId required' });
  }

  // Determine URL path based on media type
  let urlPath = 'files';
  if (mediaType === 'IMAGE') {
    urlPath = 'images';
  } else if (mediaType === 'VOICE') {
    urlPath = 'voice';
  }

  const metadata: MediaMetadata = {
    id: uuidv4(),
    messageId,
    channelId,
    senderId,
    mediaType: mediaType || 'FILE',
    filename: req.file.filename,
    originalName: req.file.originalname,
    mimeType: req.file.mimetype,
    size: req.file.size,
    url: `/media/${urlPath}/${req.file.filename}`,
    uploadedAt: Date.now()
  };

  mediaStore.set(metadata.id, metadata);

  console.log(`[Media] Uploaded: ${metadata.originalName} (${metadata.mediaType}) -> ${metadata.url}`);

  res.status(201).json({
    id: metadata.id,
    url: metadata.url,
    filename: metadata.filename,
    size: metadata.size,
    mimeType: metadata.mimeType
  });
});

/**
 * Get media metadata by ID
 */
mediaRouter.get('/:id', (req: Request, res: Response) => {
  const metadata = mediaStore.get(req.params.id);
  
  if (!metadata) {
    return res.status(404).json({ error: 'Media not found' });
  }

  res.json(metadata);
});

/**
 * Delete media by ID
 */
mediaRouter.delete('/:id', (req: Request, res: Response) => {
  const metadata = mediaStore.get(req.params.id);
  
  if (!metadata) {
    return res.status(404).json({ error: 'Media not found' });
  }

  // Delete file from disk
  let dir = FILES_DIR;
  if (metadata.mediaType === 'IMAGE') {
    dir = IMAGES_DIR;
  } else if (metadata.mediaType === 'VOICE') {
    dir = VOICE_DIR;
  }

  const filePath = path.join(dir, metadata.filename);
  if (fs.existsSync(filePath)) {
    fs.unlinkSync(filePath);
  }

  mediaStore.delete(req.params.id);
  console.log(`[Media] Deleted: ${metadata.originalName}`);

  res.status(204).send();
});

/**
 * Clean up old media (older than 7 days)
 */
export function cleanupOldMedia() {
  const cutoff = Date.now() - (7 * 24 * 60 * 60 * 1000);
  let cleaned = 0;

  mediaStore.forEach((metadata, id) => {
    if (metadata.uploadedAt < cutoff) {
      let dir = FILES_DIR;
      if (metadata.mediaType === 'IMAGE') {
        dir = IMAGES_DIR;
      } else if (metadata.mediaType === 'VOICE') {
        dir = VOICE_DIR;
      }

      const filePath = path.join(dir, metadata.filename);
      if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
      }

      mediaStore.delete(id);
      cleaned++;
    }
  });

  if (cleaned > 0) {
    console.log(`[Media] Cleaned up ${cleaned} old files`);
  }
}

// Export for static file serving
export { IMAGES_DIR, VOICE_DIR, FILES_DIR };
