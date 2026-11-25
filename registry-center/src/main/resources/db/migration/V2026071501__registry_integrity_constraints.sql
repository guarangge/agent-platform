ALTER TABLE user_role_rel
    ADD CONSTRAINT fk_user_role_rel_user
    FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE user_role_rel
    ADD CONSTRAINT fk_user_role_rel_role
    FOREIGN KEY (role_id) REFERENCES roles (id);

ALTER TABLE role_agent_rel
    ADD CONSTRAINT fk_role_agent_rel_role
    FOREIGN KEY (role_id) REFERENCES roles (id);

ALTER TABLE role_agent_rel
    ADD CONSTRAINT fk_role_agent_rel_agent
    FOREIGN KEY (agent_id) REFERENCES agents (id);

ALTER TABLE tool_bindings
    ADD CONSTRAINT fk_tool_bindings_tool
    FOREIGN KEY (tool_id) REFERENCES tools (id);

ALTER TABLE tool_bindings
    ADD CONSTRAINT fk_tool_bindings_provider
    FOREIGN KEY (provider_id) REFERENCES tool_providers (id);

ALTER TABLE tool_bindings
    ADD CONSTRAINT fk_tool_bindings_provider_instance
    FOREIGN KEY (provider_instance_id) REFERENCES tool_provider_instances (id);
