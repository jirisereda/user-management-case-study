package cz.vig.usermanagement;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return repository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    public User create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (repository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }
        // A concurrent insert of the same email is still caught by the unique constraint (-> 409).
        return repository.save(new User(request.name().strip(), email));
    }

    public User update(Long id, UpdateUserRequest request) {
        User user = findById(id);
        if (request.email() != null && !normalizeEmail(request.email()).equals(user.getEmail())) {
            throw new EmailChangeNotAllowedException();
        }
        user.rename(request.name().strip());
        return user;
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
