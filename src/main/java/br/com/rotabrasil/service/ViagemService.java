package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.RecursoNaoEncontradoException;
import br.com.rotabrasil.model.Viagem;
import br.com.rotabrasil.repository.ViagemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service @RequiredArgsConstructor
public class ViagemService {
    private final ViagemRepository repository;
    public Viagem cadastrarViagem(Viagem viagem) { return repository.save(viagem); }
    public Viagem buscarViagem(Long id) { return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Viagem não encontrada: " + id)); }
    public List<Viagem> listarViagens() { return repository.findAll(); }
    public Viagem buscarOuCadastrar(String numero, LocalDate data, String status, br.com.rotabrasil.model.Cliente cliente) {
        return repository.findByNumero(numero).orElseGet(() -> cadastrarViagem(Viagem.builder().numero(numero).data(data).status(status).cliente(cliente).build()));
    }
    public Viagem atualizarViagem(Long id, Viagem dados) { Viagem atual = buscarViagem(id); atual.setNumero(dados.getNumero()); atual.setData(dados.getData()); atual.setStatus(dados.getStatus()); atual.setCliente(dados.getCliente()); return repository.save(atual); }
    public List<Viagem> buscarViagensPorCliente(Long clienteId) { return repository.findByClienteId(clienteId); }
}
