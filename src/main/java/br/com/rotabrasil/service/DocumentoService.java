package br.com.rotabrasil.service;

import br.com.rotabrasil.exception.RecursoNaoEncontradoException;
import br.com.rotabrasil.model.Documento;
import br.com.rotabrasil.repository.DocumentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service @RequiredArgsConstructor
public class DocumentoService {
    private final DocumentoRepository repository;
    public Documento salvarDocumento(Documento documento) { return repository.save(documento); }
    public Documento buscarDocumento(Long id) { return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Documento não encontrado: " + id)); }
    public List<Documento> listarDocumentos() { return repository.findAll(); }
    public List<Documento> listarDocumentosDaViagem(Long viagemId) { return repository.findByViagemId(viagemId); }
    public Documento registrarDocumento(Documento documento) { return salvarDocumento(documento); }
}
