import java.lang.Exception
import java.util.*
import kotlin.collections.HashMap

enum class Tipo {
    TERMINAL, NAO_TERMINAL, EPSILON
}

class Regra {
    var nome:String = ""
    var subRegras = mutableListOf<SubRegra>()
}

class SubRegra {
    var elementos = mutableListOf<Elemento>()

    override fun equals(other: Any?): Boolean {
        if(other is SubRegra){
            for(elemento in elementos){
                if(!other.elementos.contains(elemento)){
                    return false
                }
            }
            return true
        }
        return false
    }

    override fun hashCode(): Int {
        return elementos.hashCode()
    }
}

class Elemento {
    var nome: String = ""
    var tipo: Tipo = Tipo.EPSILON

    override fun equals(other: Any?): Boolean {
        return other is Elemento && other.nome == nome && other.tipo == tipo
    }

    override fun hashCode(): Int {
        var result = nome.hashCode()
        result = 31 * result + tipo.hashCode()
        return result
    }
}

class NoAutomato {
    companion object {
        var idCont = 0
    }

    var id: Int = idCont++
    var nome: String = ""
    var regra: SubRegra? = SubRegra()
    var posPonto : Int = -1
    var transicoes = mutableListOf<Pair<String?, NoAutomato>>()

    override fun equals(other: Any?): Boolean {
        return other is NoAutomato && other.nome === nome && other.regra == regra
    }

    override fun hashCode(): Int {
        var result = nome.hashCode()
        result = 31 * result + (regra?.hashCode() ?: 0)
        result = 31 * result + posPonto
        result = 31 * result + transicoes.hashCode()
        return result
    }
}

class Fecho {
    companion object {
        var idCont = 0
    }

    var id: Int = idCont++
    val alcancaveis: MutableList<Int> = mutableListOf()
    val proximos: HashMap<String, Int> = hashMapOf()
}

val regras: MutableList<Regra> = mutableListOf()
val firstMap: HashMap<String, List<String>> = hashMapOf()
val followMap: HashMap<String, MutableList<String>> = hashMapOf()
val inicioAutomato = NoAutomato()
val listaNoAutomato = mutableListOf<NoAutomato>()
val noAutomatoMap : HashMap<String, MutableList<NoAutomato>> = hashMapOf()
val fechoMap : HashMap<Int, Fecho> = hashMapOf() // Key = ID do Nó, Value = Fecho do Nó
val listaFecho = mutableListOf<Fecho>()
val tabelaAction: HashMap<Pair<Int, Char>, String> = hashMapOf()
val tabelaGoto: HashMap<Pair<Int, String>, Int> = hashMapOf()

const val epsilon = "null"
const val endLineValue = "$"

fun main(){
    val gramatica = """
        <A>::=(<A>)|a
    """.trimIndent()

    val string = "(((a)))$endLineValue".replace(" ".toRegex(), "")

    val tempoInicio = System.currentTimeMillis()
    processarGramatica(gramatica)
    gerarAFNEpsilon()
    gerarFechoEpsilon(inicioAutomato)
    gerarFirst()
    gerarFollow()
    gerarTabela()
    val tempoFim = System.currentTimeMillis()
    println("String: $string -> Reconhecida: ${analisarString(string)}")
    print("Tempo total: ${tempoFim - tempoInicio}(ms)")

}

/**GRAMÁTICA**/

fun processarGramatica(texto:String){
    for(linha in texto.lines()){
        processarRegra(linha)?.let { regras.add(it) }
    }
}

fun processarRegra(texto:String): Regra? {
    val lista = texto.split("::=")
    return if(lista.size == 2){
        val regra = Regra()
        regra.nome = lista[0].replace("[<>]".toRegex(), "")
        regra.subRegras = processarSubRegra(lista[1])
        regra
    }else{
        null
    }

}

fun processarSubRegra(texto:String): MutableList<SubRegra>{
    val lista = texto.split("|")
    val subRegras = mutableListOf<SubRegra>()

    for(item in lista){
        val subRegra = SubRegra()
        subRegra.elementos = processarElementos(item)
        subRegras.add(subRegra)
    }

    return subRegras
}

fun processarElementos(texto:String): MutableList<Elemento>{

    val elementos = mutableListOf<Elemento>()

    var i = 0
    while(i < texto.length){
        val c = texto[i]

        val elemento = Elemento()
        if(c == '<'){
            val fechaPos = texto.indexOf('>', i)
            val nome = texto.substring(i+1, fechaPos)
            elemento.nome = nome
            elemento.tipo = Tipo.NAO_TERMINAL
            i = fechaPos
        }else if(c == 'n' && texto.length >= i+4 && texto.substring(i, i+4) == epsilon){
            elemento.nome = epsilon
            elemento.tipo = Tipo.EPSILON
            i+=4
        }else{
            var fimTerminal = texto.indexOf('<', i)
            if(fimTerminal == -1){
                fimTerminal = texto.length
            }

            elemento.nome = texto.substring(i, fimTerminal)
            elemento.tipo = Tipo.TERMINAL
            i = fimTerminal - 1
        }

        elementos.add(elemento)

        i++
    }

    return elementos
}

/**FIRST**/

fun gerarFirst(){
    for(regra in regras){
        gerarFirst(regra)
    }
}

fun gerarFirst(regra: Regra){
    var first = mutableListOf<String>()
    for(subRegra in regra.subRegras){
        first = first.union(gerarFirstSubRegra(subRegra, regra.nome)).toMutableList()
    }
    firstMap[regra.nome] = first
}

fun gerarFirstSubRegra(subRegra: SubRegra, nomeRegra: String): List<String>{
    var first = mutableListOf<String>()

    val primeiroElemento = subRegra.elementos[0]
    when(primeiroElemento.tipo){
        Tipo.TERMINAL -> {
            val chave = primeiroElemento.nome
            if(!firstMap.containsKey(chave)){
                firstMap[chave] = listOf(chave)
            }
            first.add(chave)
        }

        Tipo.EPSILON -> {
            firstMap[primeiroElemento.nome] = listOf(epsilon)
            first.add(epsilon)
        }

        Tipo.NAO_TERMINAL -> {
            for(elemento in subRegra.elementos){
                if(elemento.nome != nomeRegra){
                    if(!firstMap.containsKey(elemento.nome)){
                        gerarFirstElemento(elemento)
                    }
                    val firstElemento = firstMap[elemento.nome]
                    if(firstElemento != null){
                        if(first.contains(epsilon) && !firstElemento.contains(epsilon)){
                            first.remove(epsilon)
                        }
                        first = first.union(firstElemento).toMutableList()
                    }
                    if(first.isNotEmpty() && !first.contains(epsilon)){
                        break
                    }
                }
            }
        }
    }

    return first
}

fun gerarFirstElemento(elemento: Elemento){
    val chave = elemento.nome

    when(elemento.tipo){
        Tipo.TERMINAL -> {
            if(!firstMap.containsKey(chave)){
                firstMap[chave] = listOf(chave)
            }
        }

        Tipo.EPSILON -> {
            firstMap[chave] = listOf(epsilon)
        }

        Tipo.NAO_TERMINAL -> {
            regras.firstOrNull { it.nome == chave }?.let {
                gerarFirst(it)
            }
        }
    }
}

/**FOLLOW**/

fun gerarFollow(){
    if(regras.isNotEmpty()){
        val regraInicial = regras[0]
        for(regra in regras){
            followMap[regra.nome] = mutableListOf()
        }
        //Regra 1 do Follow
        followMap[regraInicial.nome]?.add(endLineValue)
        followRegra2()
        followRegra3()
    }
}

fun followRegra2(){
    for(regra in regras){
        for(subRegra in regra.subRegras){
            val numElementos = subRegra.elementos.size
            if(numElementos > 2){
                for(i in 1 until subRegra.elementos.size-1){
                    val elemento = subRegra.elementos[i]
                    val proximo = subRegra.elementos[i+1]
                    if(elemento.tipo == Tipo.NAO_TERMINAL){
                        if(proximo.tipo == Tipo.NAO_TERMINAL){
                            firstMap[proximo.nome]?.let {  firstsProximo ->
                                followMap[elemento.nome]?.union(firstsProximo)?.toMutableList()?.let {
                                    it.remove(epsilon)
                                    followMap[elemento.nome] = it
                                }
                            }
                        }else if(proximo.tipo == Tipo.TERMINAL){
                            if(followMap[elemento.nome]?.contains(proximo.nome) == false){
                                followMap[elemento.nome]?.add(proximo.nome)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun followRegra3(){
    for(regra in regras){
        for(subRegra in regra.subRegras){
            val numElementos = subRegra.elementos.size
            for(i in 0 until numElementos){
                val elemento = subRegra.elementos[i]
                val proximoPos = i+1

                val checaProximo = checa@{
                    val proximo = subRegra.elementos[proximoPos]
                    if(proximo.tipo == Tipo.NAO_TERMINAL){
                        regras.firstOrNull{ it.nome == proximo.nome }?.let { r ->
                            for(sr in r.subRegras){
                                if(sr.elementos.firstOrNull{ it.tipo == Tipo.EPSILON } != null){
                                    return@checa true
                                }
                            }
                        }
                    }
                    return@checa false
                }

                if(elemento.tipo == Tipo.NAO_TERMINAL && (proximoPos == numElementos || checaProximo())){
                    followMap[regra.nome]?.let { followRegra ->
                        followMap[elemento.nome]?.union(followRegra)?.toMutableList()?.let {
                            followMap[elemento.nome] = it
                        }
                    }
                }
            }
        }
    }
}

/**AFN-Epsilon**/

fun gerarAFNEpsilon(){
    if(regras.isNotEmpty()){

        //Regra 1
        val regraInicialNome = regras[0].nome

        val elemento = Elemento()
        elemento.nome = regraInicialNome
        elemento.tipo = Tipo.NAO_TERMINAL

        val subRegra = SubRegra()
        subRegra.elementos = mutableListOf(elemento)

        val novaRegra = Regra()
        novaRegra.nome = "X"
        novaRegra.subRegras = mutableListOf(subRegra)

        regras.add(0, novaRegra)

        val segundoNo = NoAutomato()
        segundoNo.nome = novaRegra.nome
        segundoNo.regra = subRegra
        segundoNo.posPonto = 0

        inicioAutomato.nome = "Inicio"
        inicioAutomato.regra = null
        inicioAutomato.transicoes.add(Pair(null, segundoNo))

        aplicarRegrasAFNEpsilon(segundoNo)
    }
}

fun aplicarRegrasAFNEpsilon(no: NoAutomato){
    val subRegraNo = no.regra

    if(subRegraNo != null && no.posPonto < subRegraNo.elementos.size){
        val elemento = subRegraNo.elementos[no.posPonto]
        if(elemento.tipo == Tipo.NAO_TERMINAL){
            //Regra 2
            if(noAutomatoMap[elemento.nome] == null) {
                regras.firstOrNull { it.nome == elemento.nome }?.let {
                    noAutomatoMap[it.nome] = mutableListOf()

                    for (subRegra in it.subRegras) {
                        val novoNo = NoAutomato()
                        novoNo.nome = it.nome
                        novoNo.regra = subRegra
                        novoNo.posPonto = 0

                        no.transicoes.add(Pair(null, novoNo))
                        noAutomatoMap[it.nome]?.add(novoNo)
                    }

                    for(transicao in no.transicoes){
                        aplicarRegrasAFNEpsilon(transicao.second)
                    }
                }
            }else{
                noAutomatoMap[elemento.nome]?.let { listaNos ->
                    for(noFilho in listaNos){
                        no.transicoes.add(Pair(null, noFilho))
                    }
                }
            }
        }

        //Regra 3
        val novoNo = NoAutomato()
        novoNo.nome = no.nome
        novoNo.regra = subRegraNo
        novoNo.posPonto = no.posPonto + 1
        no.transicoes.add(Pair(elemento.nome, novoNo))
        aplicarRegrasAFNEpsilon(novoNo)
    }

    listaNoAutomato.add(no)
}

/**Fecho-Epsilon**/

fun gerarFechoEpsilon(no: NoAutomato): Fecho {
    val fecho = Fecho()
    fecho.alcancaveis.add(no.id)

    fechoMap[no.id] = fecho

    val transicoes = no.transicoes

    var i = 0
    while(i < transicoes.size){
        val transicao = transicoes[i]
        val t = transicao.first
        if(t == null){
            fecho.alcancaveis.add(transicao.second.id)
            for (proximaTransicao in transicao.second.transicoes){
                if(!transicoes.contains(proximaTransicao)){
                    transicoes.add(proximaTransicao)
                }
            }
        }else{
            val fechoProximo = if(fechoMap[transicao.second.id] == null){
                gerarFechoEpsilon(transicao.second)
            }else{
                fechoMap[transicao.second.id]
            }
            fecho.proximos[t] = fechoProximo?.id ?: -1
        }
        i++
    }

    fechoMap[no.id] = fecho
    listaFecho.add(fecho)

    return fecho

}

/**Tabela**/

fun gerarTabela(){
    if(regras.isNotEmpty()){
        //Adicionando o ACC
        tabelaAction[Pair(getIdFechoSubRegra(regras[0].subRegras[0]), '$')] = "ACC"

        //Gerando os Shifts e Goto
        for(chave in fechoMap.keys){
            fechoMap[chave]?.let { fecho ->
                for(string in fecho.proximos.keys){
                    fecho.proximos[string]?.let { idProximo ->
                        if(regras.firstOrNull{ it.nome == string } == null){
                            tabelaAction[Pair(fecho.id, string[0])] = "S$idProximo"
                        }else{
                            tabelaGoto[Pair(fecho.id, string)] = idProximo
                        }
                    }
                }
            }
        }

        //Gerando o Reduces
        var cont = 0
        for(regra in regras){
            for(subRegra in regra.subRegras){
                if(cont > 0){
                    val idFecho = getIdFechoSubRegra(subRegra)
                    followMap[regra.nome]?.let {
                        for(string in it){
                            tabelaAction[Pair(idFecho, string[0])] = "R$cont"
                        }
                    }
                }
                cont++
            }
        }
    }
}

fun getIdFechoSubRegra(subRegra: SubRegra): Int {
    var id = -1
    for(no in listaNoAutomato){
        if(no.posPonto == subRegra.elementos.size && no.regra == subRegra){
            id = fechoMap[no.id]?.id ?: -1
        }
    }

    return id
}

fun analisarString(string: String): Boolean{
    val pilha = Stack<String>()
    pilha.push("0")

    var cursor = 0

    while(true){
        pilha.peek()?.let { valor ->
            if(valor.toIntOrNull() != null){
                val estado = valor.toInt()
                val char = string[cursor]
                tabelaAction[Pair(estado, char)]?.let { comando ->
                    if(comando == "ACC"){
                        return true
                    }

                    val proxEstado = comando.substring(1).toInt()
                    when(comando[0]){
                        'S' -> {
                            pilha.push(char.toString())
                            pilha.push(proxEstado.toString())
                            cursor++
                        }
                        'R' -> {
                            getSubRegra(proxEstado)?.let {
                                try{
                                    for(i in 0 until it.second.elementos.size * 2){
                                        pilha.pop() ?: return false
                                    }
                                }catch (e: Exception){
                                    return false
                                }
                                pilha.push(it.first)

                            } ?: return false
                        }
                        else -> return false
                    }
                } ?: return false
            }else{
                pilha[pilha.size - 2]?.toIntOrNull()?.let { estado ->
                    val naoTerminal = pilha.peek()
                    tabelaGoto[Pair(estado, naoTerminal)]?.let {
                        pilha.push(it.toString())
                    } ?: return false
                } ?: return false
            }
        } ?: return false
    }
}

fun getSubRegra(pos: Int): Pair<String, SubRegra>?{
    var cont = 0
    for(regra in regras){
        for(subRegra in regra.subRegras){
            if(cont == pos){
                return Pair(regra.nome, subRegra)
            }
            cont++
        }
    }
    return null
}