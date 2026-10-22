import type { ThemeConfig } from 'antd';

export const theme: ThemeConfig = {
  token: {
    colorPrimary: '#0f766e',
    colorLink: '#0f766e',
    borderRadius: 8,
    fontFamily: "'Be Vietnam Pro', -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
  },
  components: {
    Layout: { headerBg: '#0f3d3a', headerPadding: '0 24px' },
    Menu: { itemSelectedBg: '#ccfbf1', itemSelectedColor: '#0f766e' },
  },
};
