package br.com.smartprod.usuario;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** O e-mail é sempre gravado em minúsculas (ver construtor de {@link Usuario}). */
    Optional<Usuario> findByEmail(String email);
}
