package com.thang.user.service.role;

import com.thang.user.model.Role;
import com.thang.user.repository.IRoleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RoleServiceImpl implements IRoleService {

    private final IRoleRepository roleRepository;

    public RoleServiceImpl(IRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void addNewRole(Role role) {
        this.roleRepository.save(role);
    }

    @Override
    public List<Role> getAllRoles() {
        return this.roleRepository.findAll();
    }

    @Override
    public Role findRoleByRoleName(String roleName) {
        Optional<Role> role = this.roleRepository.findRoleByRoleName(roleName);
        return role.orElse(null);
    }
}
