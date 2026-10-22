import { authHandlers } from './auth';
import { catalogHandlers } from './catalog';
import { screeningHandlers } from './screening';
import { applicationHandlers } from './applications';
import { meHandlers } from './me';
import { staffHandlers } from './staff';
import { adminHandlers } from './admin';
import { reportHandlers } from './reports';

export const handlers = [
  ...authHandlers, ...catalogHandlers, ...screeningHandlers, ...applicationHandlers, ...meHandlers,
  ...staffHandlers, ...adminHandlers, ...reportHandlers,
];
