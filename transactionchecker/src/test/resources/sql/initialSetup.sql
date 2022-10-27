-- =====
-- BLOCK
-- =====
CREATE TABLE IF NOT EXISTS block (
  number BIGINT      PRIMARY KEY NOT NULL,
  hash   VARCHAR(64)             NOT NULL
);

CREATE UNIQUE INDEX idx1_block ON block(hash);

-- ===========
-- TRANSACTION
-- ===========
CREATE TABLE IF NOT EXISTS transaction (
  block_number BIGINT      NOT NULL,
  number       BIGINT      NOT NULL,
  txid         VARCHAR(64) NOT NULL
);

CREATE UNIQUE INDEX idx1_transaction ON transaction(txid);
CREATE UNIQUE INDEX idx2_transaction ON transaction(block_number, number);

-- =======
-- ADDRESS
-- =======
CREATE TABLE IF NOT EXISTS address (
  number BIGINT PRIMARY KEY NOT NULL,
  address       VARCHAR(64) NOT NULL,
  hex           VARCHAR(68) NOT NULL
);

CREATE UNIQUE INDEX idx1_address ON address(address);

-- =========
-- LIQUIDITY
-- =========
CREATE TABLE IF NOT EXISTS liquidity (
  address_number           BIGINT NOT NULL,
  start_block_number       BIGINT NOT NULL,
  start_transaction_number BIGINT NOT NULL,
  change_time              TIMESTAMP WITH TIME ZONE
                           GENERATED ALWAYS AS CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx1_liquidity ON liquidity(address_number);

-- =======
-- DEPOSIT
-- =======
CREATE TABLE IF NOT EXISTS deposit (
  liquidity_address_number BIGINT NOT NULL,
  deposit_address_number   BIGINT NOT NULL,
  customer_address_number  BIGINT NOT NULL,
  start_block_number       BIGINT NOT NULL,
  start_transaction_number BIGINT NOT NULL,
  change_time              TIMESTAMP WITH TIME ZONE
                           GENERATED ALWAYS AS CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx1_deposit ON deposit(liquidity_address_number, customer_address_number, deposit_address_number);

-- =======
-- BALANCE
-- =======
CREATE TABLE IF NOT EXISTS balance (
  address_number    BIGINT        NOT NULL,
  block_number      BIGINT        NOT NULL,
  transaction_count BIGINT        NOT NULL,
  vout              DECIMAL(20,8) NOT NULL,
  vin               DECIMAL(20,8) NOT NULL,
  change_time       TIMESTAMP WITH TIME ZONE
                    GENERATED ALWAYS AS CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx1_balance ON balance(address_number);

-- =======
-- STAKING
-- =======
CREATE TABLE IF NOT EXISTS staking (
  liquidity_address_number BIGINT        NOT NULL,
  deposit_address_number   BIGINT        NOT NULL,
  customer_address_number  BIGINT        NOT NULL,
  last_in_block_number     BIGINT        NOT NULL,
  vin                      DECIMAL(20,8) NOT NULL,
  last_out_block_number    BIGINT        NOT NULL,
  vout                     DECIMAL(20,8) NOT NULL,
  change_time              TIMESTAMP WITH TIME ZONE
                           GENERATED ALWAYS AS CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx1_staking ON staking(liquidity_address_number, deposit_address_number, customer_address_number);

-- ===================
-- API_DUPLICATE_CHECK
-- ===================
CREATE TABLE IF NOT EXISTS api_duplicate_check (
  withdrawal_id  BIGINT      NOT NULL,
  transaction_id VARCHAR(64) NOT NULL,
  change_time    TIMESTAMP WITH TIME ZONE
                 GENERATED ALWAYS AS CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx1_api_duplicate_check ON api_duplicate_check(withdrawal_id);

-- ====================
-- MASTERNODE_WHITELIST
-- ====================
CREATE TABLE IF NOT EXISTS masternode_whitelist (
  wallet_id     BIGINT      NOT NULL,
  idx           BIGINT      NOT NULL,
  owner_address VARCHAR(64) NOT NULL,
  create_time   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  change_time   TIMESTAMP WITH TIME ZONE
                GENERATED ALWAYS AS CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx1_masternode_whitelist ON masternode_whitelist(wallet_id, idx, owner_address);
CREATE UNIQUE INDEX idx2_masternode_whitelist ON masternode_whitelist(owner_address);
