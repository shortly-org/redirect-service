-- Local compose only: the password equals the role name.
CREATE ROLE redirect_reader LOGIN PASSWORD 'redirect_reader' CONNECTION LIMIT 64 IN ROLE link_reader;
