package br.com.rotabrasil.service;

import br.com.rotabrasil.dto.AnexoResponse;
import br.com.rotabrasil.exception.*;
import br.com.rotabrasil.model.Anexo;
import br.com.rotabrasil.repository.AnexoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AnexoService {
    private final AnexoRepository anexos;
    private final ViagemService viagens;
    private final CoordenacaoImportacaoService coordenacao;
    public List<AnexoResponse> listar(Long viagemId, boolean arquivados) {
        viagens.buscarViagem(viagemId);
        return (arquivados ? anexos.findByViagensIdAndArquivadoEmIsNotNull(viagemId) : anexos.findByViagensIdAndArquivadoEmIsNull(viagemId))
                .stream().map(AnexoResponse::de).toList();
    }
    public Anexo buscar(Long id) {return anexos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado"));}
    public AnexoResponse conferir(Long id, Long viagemId) {
        var anexo=buscar(id);
        if(anexo.getArquivadoEm()!=null || anexo.getViagens().stream().noneMatch(v -> v.getId().equals(viagemId)))
            throw new ConflitoRecursoException("Anexo arquivado ou sem vínculo com a viagem selecionada");
        return AnexoResponse.de(anexo);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void arquivar(Long id) {
        coordenacao.bloquear();var anexo=buscar(id);
        if(anexo.getArquivadoEm()==null){anexo.setArquivadoEm(OffsetDateTime.now(ZoneOffset.UTC));anexos.saveAndFlush(anexo);}
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AnexoResponse restaurar(Long id) {
        coordenacao.bloquear();var anexo=buscar(id);anexo.setArquivadoEm(null);return AnexoResponse.de(anexos.saveAndFlush(anexo));
    }
}
