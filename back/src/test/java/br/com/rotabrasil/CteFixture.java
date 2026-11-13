package br.com.rotabrasil;

/** Fixture sintética, sem autorização fiscal real. */
final class CteFixture {
    static final String CHAVE = "35250412345678000195570020000662131001747719";

    static String xml() {
        return """
                <cteProc xmlns="http://www.portalfiscal.inf.br/cte" versao="4.00">
                  <CTe><infCte Id="CTe%s" versao="4.00">
                    <ide><nCT>66213</nCT><dhEmi>2025-04-04T08:14:10-03:00</dhEmi></ide>
                    <dest><CNPJ>00000000000000</CNPJ><xNome>Cliente Teste</xNome>
                      <enderDest><fone>1100000000</fone></enderDest>
                    </dest>
                  </infCte></CTe>
                  <protCTe versao="4.00"><infProt><chCTe>%s</chCTe>
                    <cStat>100</cStat><xMotivo>Autorizado</xMotivo>
                  </infProt></protCTe>
                </cteProc>
                """.formatted(CHAVE, CHAVE);
    }

    static String outraChave() {
        // Outra série, mesmo nCT; DV independente, pré-calculado para a fixture.
        return "35250412345678000195570030000662131001747715";
    }
}
