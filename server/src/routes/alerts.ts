import { Router } from 'express';
import { prisma } from '../db.js';

const router = Router();

// 获取预警列表
router.get('/', async (req, res) => {
  try {
    const { page = '1', limit = '50', unreadOnly, unhandledOnly } = req.query;

    const pageNum = parseInt(page as string);
    const limitNum = parseInt(limit as string);
    const skip = (pageNum - 1) * limitNum;

    const where: any = {};
    if (unreadOnly === 'true') where.isRead = false;
    if (unhandledOnly === 'true') where.handled = false;

    const [alerts, total, unreadCount, unhandledCount] = await Promise.all([
      prisma.alert.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        skip,
        take: limitNum
      }),
      prisma.alert.count({ where }),
      prisma.alert.count({ where: { isRead: false } }),
      prisma.alert.count({ where: { handled: false } })
    ]);

    res.json({
      data: alerts,
      unreadCount,
      unhandledCount,
      pagination: {
        page: pageNum,
        limit: limitNum,
        total,
        totalPages: Math.ceil(total / limitNum)
      }
    });
  } catch (error) {
    console.error('Error fetching alerts:', error);
    res.status(500).json({ error: 'Failed to fetch alerts' });
  }
});

// 标记为已读
router.patch('/:id/read', async (req, res) => {
  try {
    const alert = await prisma.alert.update({
      where: { id: req.params.id },
      data: { isRead: true }
    });
    res.json(alert);
  } catch (error: any) {
    if (error.code === 'P2025') {
      return res.status(404).json({ error: 'Alert not found' });
    }
    console.error('Error marking alert as read:', error);
    res.status(500).json({ error: 'Failed to mark as read' });
  }
});

// 全部标记为已读
router.patch('/read-all', async (_req, res) => {
  try {
    await prisma.alert.updateMany({ where: { isRead: false }, data: { isRead: true } });
    res.json({ message: 'All alerts marked as read' });
  } catch (error) {
    console.error('Error marking all as read:', error);
    res.status(500).json({ error: 'Failed to mark all as read' });
  }
});

// 标记业务已处置
router.patch('/:id/handle', async (req, res) => {
  try {
    const alert = await prisma.alert.update({
      where: { id: req.params.id },
      data: { handled: true, isRead: true }
    });
    res.json(alert);
  } catch (error: any) {
    if (error.code === 'P2025') {
      return res.status(404).json({ error: 'Alert not found' });
    }
    console.error('Error handling alert:', error);
    res.status(500).json({ error: 'Failed to handle alert' });
  }
});

// 删除单条
router.delete('/:id', async (req, res) => {
  try {
    await prisma.alert.delete({ where: { id: req.params.id } });
    res.status(204).send();
  } catch (error: any) {
    if (error.code === 'P2025') {
      return res.status(404).json({ error: 'Alert not found' });
    }
    console.error('Error deleting alert:', error);
    res.status(500).json({ error: 'Failed to delete alert' });
  }
});

// 清空
router.delete('/', async (_req, res) => {
  try {
    await prisma.alert.deleteMany({});
    res.json({ message: 'All alerts deleted' });
  } catch (error) {
    console.error('Error clearing alerts:', error);
    res.status(500).json({ error: 'Failed to clear alerts' });
  }
});

export default router;
