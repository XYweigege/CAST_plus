import nodemailer from 'nodemailer';

export interface AlertMailPayload {
  title: string;
  content: string;
  urgency: string;
  topics: string[];
  productLine: string | null;
  rating: number | null;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
let transporter: any = null;

function getTransporter(): any {
  if (!process.env.SMTP_HOST || !process.env.SMTP_USER || !process.env.SMTP_PASS) {
    return null;
  }

  if (!transporter) {
    transporter = nodemailer.createTransport({
      host: process.env.SMTP_HOST,
      port: parseInt(process.env.SMTP_PORT || '587'),
      secure: process.env.SMTP_SECURE === 'true',
      auth: {
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS
      }
    });
  }

  return transporter;
}

const URGENCY_STYLE: Record<string, { label: string; color: string }> = {
  critical: { label: '紧急', color: '#dc2626' },
  action: { label: '需处理', color: '#ea580c' },
  attention: { label: '需关注', color: '#d97706' },
  info: { label: '一般', color: '#059669' }
};

/** 发送单条客户反馈预警邮件 */
export async function sendAlertEmail(payload: AlertMailPayload): Promise<boolean> {
  const mailer = getTransporter();

  if (!mailer || !process.env.NOTIFY_EMAIL) {
    return false;
  }

  const style = URGENCY_STYLE[payload.urgency] ?? URGENCY_STYLE.info;

  try {
    await mailer.sendMail({
      from: process.env.SMTP_USER,
      to: process.env.NOTIFY_EMAIL,
      subject: `[${style.label}] 客户反馈预警：${payload.topics[0] ?? '需跟进'}`,
      html: `
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
            .container { max-width: 600px; margin: 0 auto; padding: 20px; }
            .header { background: #1e3a8a; color: white; padding: 20px; border-radius: 8px 8px 0 0; }
            .content { background: #f8f9fa; padding: 20px; border-radius: 0 0 8px 8px; }
            .badge { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; color: #fff; }
            .meta { color: #666; font-size: 14px; margin: 10px 0; }
            .summary { background: #fff; border-left: 4px solid #1e3a8a; padding: 12px 16px; margin: 16px 0; }
          </style>
        </head>
        <body>
          <div class="container">
            <div class="header">
              <h2 style="margin: 0;">客户反馈预警</h2>
              <p style="margin: 8px 0 0; opacity: 0.85; font-size: 13px;">保险客户声音智能分析系统</p>
            </div>
            <div class="content">
              <p><span class="badge" style="background:${style.color}">${style.label}</span></p>
              <h3 style="margin-top: 16px;">${payload.title}</h3>
              <div class="summary">${payload.content}</div>
              <div class="meta">
                <p><strong>主题标签：</strong>${payload.topics.join('、') || '—'}</p>
                <p><strong>产品线：</strong>${payload.productLine ?? '—'}</p>
                <p><strong>客户评分：</strong>${payload.rating != null ? payload.rating + ' / 5' : '—'}</p>
                <p><strong>触发时间：</strong>${new Date().toLocaleString('zh-HK')}</p>
              </div>
            </div>
          </div>
        </body>
        </html>
      `
    });

    return true;
  } catch (error) {
    console.error('Failed to send alert email:', error);
    return false;
  }
}

export interface DigestRow {
  id: string;
  content: string;
  urgency: string;
  sentiment: string;
  topics: string | null;
  aiSummary: string | null;
  productLine: string | null;
}

/** 发送每日客户声音摘要邮件 */
export async function sendDigestEmail(rows: DigestRow[]): Promise<boolean> {
  const mailer = getTransporter();

  if (!mailer || !process.env.NOTIFY_EMAIL || rows.length === 0) {
    return false;
  }

  const negativeCount = rows.filter(r => r.sentiment === 'negative').length;

  const rowsHtml = rows.map(r => {
    const style = URGENCY_STYLE[r.urgency] ?? URGENCY_STYLE.info;
    return `
      <tr>
        <td style="padding: 10px; border-bottom: 1px solid #eee; max-width: 320px;">
          ${(r.aiSummary || r.content).slice(0, 70)}
        </td>
        <td style="padding: 10px; border-bottom: 1px solid #eee;">${r.productLine ?? '—'}</td>
        <td style="padding: 10px; border-bottom: 1px solid #eee;">
          <span style="color:${style.color};font-weight:600">${style.label}</span>
        </td>
      </tr>
    `;
  }).join('');

  try {
    await mailer.sendMail({
      from: process.env.SMTP_USER,
      to: process.env.NOTIFY_EMAIL,
      subject: `客户声音日报：${rows.length} 条反馈，${negativeCount} 条负面`,
      html: `
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"></head>
        <body style="font-family: -apple-system, BlinkMacSystemFont, sans-serif;">
          <h2>客户声音日报</h2>
          <p>过去 24 小时新增 <strong>${rows.length}</strong> 条反馈，其中负面 <strong>${negativeCount}</strong> 条。</p>
          <table style="width: 100%; border-collapse: collapse; font-size: 14px;">
            <thead>
              <tr style="background: #f1f5f9;">
                <th style="padding: 10px; text-align: left;">AI 归因</th>
                <th style="padding: 10px; text-align: left;">产品线</th>
                <th style="padding: 10px; text-align: left;">紧急度</th>
              </tr>
            </thead>
            <tbody>${rowsHtml}</tbody>
          </table>
        </body>
        </html>
      `
    });

    return true;
  } catch (error) {
    console.error('Failed to send digest email:', error);
    return false;
  }
}
