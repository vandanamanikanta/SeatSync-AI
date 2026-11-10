package com.seatsync.user;

import com.seatsync.modules.user.entity.Role;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.entity.UserPreference;
import com.seatsync.modules.user.entity.UserStatus;
import com.seatsync.modules.user.repository.RoleRepository;
import com.seatsync.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Should persist user, assign role, and cascade user preference")
    void shouldPersistUserWithRoleAndPreference() {
        Role customerRole = roleRepository.save(new Role("ROLE_CUSTOMER", "Customer"));

        User user = User.builder()
                .email("john.doe@example.com")
                .passwordHash("$2a$10$encryptedhash")
                .fullName("John Doe")
                .phoneNumber("+919876543210")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(customerRole))
                .build();

        UserPreference preference = UserPreference.builder()
                .user(user)
                .preferredCity("Hyderabad")
                .budgetMin(new BigDecimal("500.00"))
                .budgetMax(new BigDecimal("3000.00"))
                .favoriteCategories("[\"concerts-music\"]")
                .build();

        user.setPreference(preference);

        User savedUser = userRepository.save(user);

        assertNotNull(savedUser.getId());
        assertEquals("john.doe@example.com", savedUser.getEmail());

        Optional<User> fetched = userRepository.findByEmailWithRoles("john.doe@example.com");
        assertTrue(fetched.isPresent());
        assertEquals(1, fetched.get().getRoles().size());
        assertTrue(fetched.get().getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_CUSTOMER")));
    }

    @Test
    @DisplayName("Should check existence by email")
    void shouldCheckExistenceByEmail() {
        User user = User.builder()
                .email("exist@example.com")
                .passwordHash("secret")
                .fullName("Exist User")
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);

        assertTrue(userRepository.existsByEmail("exist@example.com"));
        assertFalse(userRepository.existsByEmail("unknown@example.com"));
    }
}

