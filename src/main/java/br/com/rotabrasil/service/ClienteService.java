package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.RecursoNaoEncontradoException;
import br.com.rotabrasil.model.Cliente;
import br.com.rotabrasil.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service @RequiredArgsConstructor
public class ClienteService {
    private final ClienteRepository repository;
    public Cliente cadastrarCliente(Cliente cliente) { return repository.save(cliente); }
    public Cliente buscarCliente(Long id) { return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado: " + id)); }
    public List<Cliente> listarClientes() { return repository.findAll(); }
    public Cliente buscarOuCadastrar(String nome, String cnpj, String email, String telefone) {
        return repository.findByCnpj(cnpj).orElseGet(() -> cadastrarCliente(Cliente.builder().nome(nome).cnpj(cnpj).email(email).telefone(telefone).build()));
    }
    public Cliente atualizarCliente(Long id, Cliente dados) { Cliente atual = buscarCliente(id); atual.setNome(dados.getNome()); atual.setCnpj(dados.getCnpj()); atual.setEmail(dados.getEmail()); atual.setTelefone(dados.getTelefone()); return repository.save(atual); }
    public void excluirCliente(Long id) { repository.delete(buscarCliente(id)); }
    public Cliente buscarPorCnpj(String cnpj) { return repository.findByCnpj(cnpj).orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para o CNPJ informado")); }
}
