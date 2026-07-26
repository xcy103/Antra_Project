-- Creates one database + owner role per service (Database per Service).
-- Run automatically by the postgres container on first startup.
-- These are local-dev credentials only; real deployments supply their own.

CREATE USER user_svc WITH PASSWORD 'user_svc';
CREATE DATABASE userdb OWNER user_svc;

CREATE USER book_svc WITH PASSWORD 'book_svc';
CREATE DATABASE bookdb OWNER book_svc;

CREATE USER order_svc WITH PASSWORD 'order_svc';
CREATE DATABASE orderdb OWNER order_svc;

CREATE USER payment_svc WITH PASSWORD 'payment_svc';
CREATE DATABASE paymentdb OWNER payment_svc;

CREATE USER notification_svc WITH PASSWORD 'notification_svc';
CREATE DATABASE notificationdb OWNER notification_svc;

CREATE USER analytics_svc WITH PASSWORD 'analytics_svc';
CREATE DATABASE analyticsdb OWNER analytics_svc;
