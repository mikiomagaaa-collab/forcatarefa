package app.lily.ai.models

import androidx.compose.runtime.Immutable

/** Para que serve um modelo (uma ficha pode ter mais de uma categoria). */
enum class Category(val label: String) {
    CHAT("Conversa"),
    VISION("Visão"),
    ENHANCE("Aprimoramento"),
    IMAGE_GEN("Geração de imagem"),
    SPEECH("Fala"),
    TRANSLATE("Tradução"),
}

/** O mecanismo que executa o modelo no aparelho. */
enum class Runtime(val label: String) {
    LITERT_LM("LiteRT-LM (Google)"),
    LITERT("LiteRT (Google)"),
    IRIS("LiteRT · pipeline iris-3b"),
    ML_KIT("ML Kit (Google)"),
    ANDROID("Serviço do Android"),
}

enum class Modality(val label: String) { TEXT("Texto"), IMAGE("Imagem"), AUDIO("Áudio") }

/** Um arquivo de um modelo: nome no repositório, tamanho exato e SHA-256 (conferido após o download). */
@Immutable
data class ModelFile(val path: String, val bytes: Long, val sha256: String?) {
    val name: String get() = path.substringAfterLast('/')
}

/**
 * A ficha de um modelo. Tudo aqui vem das páginas oficiais dos modelos (Hugging Face), fixado num
 * commit ([revision]) para o arquivo nunca mudar por baixo: tamanho e SHA-256 conferem byte a byte.
 */
@Immutable
data class ModelSpec(
    val id: String,
    val name: String,
    val developer: String,
    val architecture: String,
    val parameters: String,
    val quantization: String,
    val categories: Set<Category>,
    val modalities: Set<Modality>,
    val runtime: Runtime,
    val description: String,
    val purposes: List<String>,
    val license: String,
    val licenseUrl: String?,
    /**
     * RAM total que o Android informa a partir da qual o modelo roda (um aparelho de 8 GB informa
     * cerca de 7,4 GB: o resto fica com o sistema). Veja [ramClassGb].
     */
    val minRamGb: Double,
    val repo: String? = null,
    val revision: String? = null,
    val files: List<ModelFile> = emptyList(),
    val experimental: Boolean = false,
    /** Vem com o app ou com o Android (sem download pelo Model Hub). */
    val builtIn: Boolean = false,
    /** Precisa do acelerador gráfico (OpenCL) do aparelho. */
    val needsGpu: Boolean = false,
    val contextTokens: Int? = null,
    /** Números publicados na página oficial, com a fonte (nunca medidos por nós). */
    val published: String? = null,
    val notes: String? = null,
    val connection: String = "Só para baixar; depois funciona sem internet",
) {
    val totalBytes: Long get() = files.sumOf { it.bytes }

    /** A categoria do aparelho que o modelo pede, como se anuncia a RAM (4, 6, 8 ou 12 GB). */
    val ramClassGb: Int
        get() = when {
            minRamGb <= 2.5 -> 3
            minRamGb <= 3.5 -> 4
            minRamGb <= 5.5 -> 6
            minRamGb <= 7.5 -> 8
            else -> 12
        }

    fun url(file: ModelFile): String = "https://huggingface.co/$repo/resolve/$revision/${file.path}"

    val pageUrl: String? get() = repo?.let { "https://huggingface.co/$it" }

    fun has(m: Modality): Boolean = m in modalities
}

/** Os modelos que a L.I.L.Y AI sabe usar. */
object Catalog {
    private const val APACHE = "https://www.apache.org/licenses/LICENSE-2.0"

    val qwen3Small = ModelSpec(
        id = "qwen3-0.6b",
        name = "Qwen3 0.6B",
        developer = "Alibaba (Qwen) · conversão litert-community",
        architecture = "Transformer decodificador (Qwen3)",
        parameters = "0,6 bilhão",
        quantization = "int4 dinâmico (pesos int4, ativações fp32)",
        categories = setOf(Category.CHAT),
        modalities = setOf(Modality.TEXT),
        runtime = Runtime.LITERT_LM,
        description = "O menor e mais rápido. Bom para correções, resumos curtos e respostas rápidas, gastando pouca bateria.",
        purposes = listOf("Correção", "Resumo curto", "Respostas rápidas", "Teclado"),
        license = "Apache 2.0",
        licenseUrl = APACHE,
        minRamGb = 3.4,
        repo = "litert-community/Qwen3-0.6B",
        revision = "8d42ab5f1db14cad987fac57aeb17d070f9e180d",
        files = listOf(ModelFile("Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm", 343490560L, "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0")),
        contextTokens = 2048,
    )

    val qwen3 = ModelSpec(
        id = "qwen3-1.7b",
        name = "Qwen3 1.7B",
        developer = "Alibaba (Qwen) · conversão litert-community",
        architecture = "Transformer decodificador (Qwen3)",
        parameters = "1,7 bilhão",
        quantization = "int4 dinâmico (pesos int4, ativações fp32)",
        categories = setOf(Category.CHAT, Category.TRANSLATE),
        modalities = setOf(Modality.TEXT),
        runtime = Runtime.LITERT_LM,
        description = "Equilíbrio entre qualidade e velocidade para conversar, reescrever e traduzir em português.",
        purposes = listOf("Conversa", "Reescrita", "Tradução", "Resumo"),
        license = "Apache 2.0",
        licenseUrl = APACHE,
        minRamGb = 5.2,
        repo = "litert-community/Qwen3-1.7B",
        revision = "c421cc3775575372ac2ca4aa1bebc97300ae36b6",
        files = listOf(ModelFile("Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm", 977184032L, "2a72330b504a6596d53a2c00923c57173b10f1b83e6a541213ffec08d1fc5de8")),
        contextTokens = 4096,
    )

    val gemma4 = ModelSpec(
        id = "gemma-4-e2b",
        name = "Gemma 4 E2B",
        developer = "Google DeepMind",
        architecture = "Transformer multimodal (Gemma 4, efetivo 2B)",
        parameters = "2 bilhões efetivos",
        quantization = "Mistura de 2, 4 e 8 bits (treino ciente de quantização)",
        categories = setOf(Category.CHAT, Category.VISION, Category.SPEECH, Category.TRANSLATE),
        modalities = setOf(Modality.TEXT, Modality.IMAGE, Modality.AUDIO),
        runtime = Runtime.LITERT_LM,
        description = "O mais capaz: entende texto, imagens e áudio. Descreve fotos, transcreve e resume áudios e conversa com mais qualidade.",
        purposes = listOf("Conversa", "Descrever imagens", "Transcrever áudio", "Tradução", "Escrita"),
        license = "Apache 2.0",
        licenseUrl = APACHE,
        minRamGb = 5.2,
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        revision = "2d74ac41a33d7da82f39f7d5720a04c68514a103",
        files = listOf(ModelFile("gemma-4-E2B-it.litertlm", 2588147712L, "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c")),
        contextTokens = 4096,
        published = "Google, Galaxy S26 Ultra (LiteRT-LM, contexto 2048): 46,9 tokens/s na CPU e 52,1 na GPU; 1,7 GB de memória na CPU.",
    )

    val ministral = ModelSpec(
        id = "ministral-3-3b",
        name = "Ministral 3 3B",
        developer = "Mistral AI · conversão litert-community",
        architecture = "Transformer decodificador (Ministral 3)",
        parameters = "3 bilhões",
        quantization = "int4 em blocos de 32",
        categories = setOf(Category.CHAT),
        modalities = setOf(Modality.TEXT),
        runtime = Runtime.LITERT_LM,
        description = "Texto de alta qualidade para escrita longa e explicações. Mais lento e pesado que o Qwen3 1.7B.",
        purposes = listOf("Escrita", "Explicações", "Organizar informações"),
        license = "Apache 2.0",
        licenseUrl = APACHE,
        minRamGb = 7.0,
        repo = "litert-community/Ministral-3-3B-Instruct-2512",
        revision = "4a82790a583e38c8101b7632aafa0ab84a7dea7d",
        files = listOf(ModelFile("Ministral-3-3B-Instruct-2512_q4_block32_ekv4096.litertlm", 2342895616L, "2e01bdbef438c1a57da21cfc9c040b271b563c0f77b8b7d1907a6154f962d0f1")),
        contextTokens = 4096,
    )

    val phi4 = ModelSpec(
        id = "phi-4-mini",
        name = "Phi-4 mini",
        developer = "Microsoft · conversão litert-community",
        architecture = "Transformer decodificador (Phi-4)",
        parameters = "3,8 bilhões",
        quantization = "int8",
        categories = setOf(Category.CHAT),
        modalities = setOf(Modality.TEXT),
        runtime = Runtime.LITERT_LM,
        description = "Raciocínio e matemática. É o maior arquivo: no Razr de 8 GB funciona com outros apps fechados.",
        purposes = listOf("Raciocínio", "Matemática", "Explicações passo a passo"),
        license = "MIT",
        licenseUrl = "https://opensource.org/license/mit",
        minRamGb = 7.0,
        repo = "litert-community/Phi-4-mini-instruct",
        revision = "56afc01737f19f1b3fc9713be1f9cc859e4684b6",
        files = listOf(ModelFile("Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm", 3910090752L, "7764d4deb53800578307be33039476b38a6c370fff71bedb3c0552563e23ab02")),
        contextTokens = 4096,
        notes = "Arquivo de 3,9 GB: precisa de bastante memória livre.",
    )

    val smolVlm = ModelSpec(
        id = "smolvlm2-500m",
        name = "SmolVLM2 500M",
        developer = "Hugging Face · conversão litert-community",
        architecture = "SigLIP + SmolLM2 (visão e linguagem)",
        parameters = "0,5 bilhão",
        quantization = "int8",
        categories = setOf(Category.VISION),
        modalities = setOf(Modality.TEXT, Modality.IMAGE),
        runtime = Runtime.LITERT_LM,
        description = "Visão leve: descreve uma foto e responde perguntas sobre ela gastando pouco. Responde melhor em inglês.",
        purposes = listOf("Descrever imagens", "Perguntas sobre uma foto"),
        license = "Apache 2.0",
        licenseUrl = APACHE,
        minRamGb = 3.4,
        repo = "litert-community/SmolVLM2-500M",
        revision = "e6ad7cf84c6f4e5f792dd65b3db431c738cb3840",
        files = listOf(ModelFile("SmolVLM2-500M.litertlm", 363085824L, "7b432bbc0a5c54ba15ecb2f039a7711c1cfb1fe38badec01397a076b5043f74e")),
        contextTokens = 2048,
        published = "litert-community, Galaxy S26 (GPU): 79,8 tokens/s; 0,43 GB de memória.",
        notes = "Uma imagem por conversa (na GPU, uma segunda imagem na mesma conversa pode piorar a resposta).",
    )

    val qwenVl = ModelSpec(
        id = "qwen3.5-0.8b-vl",
        name = "Qwen3.5 0.8B Vision",
        developer = "Alibaba (Qwen) · conversão litert-community",
        architecture = "Transformer multimodal (Qwen3.5-VL)",
        parameters = "0,8 bilhão",
        quantization = "int8",
        categories = setOf(Category.VISION, Category.CHAT),
        modalities = setOf(Modality.TEXT, Modality.IMAGE),
        runtime = Runtime.LITERT_LM,
        description = "Visão com bom português: descreve fotos, lê o que há nelas e responde perguntas.",
        purposes = listOf("Descrever imagens", "Analisar fotos", "Conversa"),
        license = "Apache 2.0",
        licenseUrl = APACHE,
        minRamGb = 5.2,
        repo = "litert-community/Qwen3.5-0.8B",
        revision = "d31d4139c495584ddf4ca78b07c92dbf7887f66d",
        files = listOf(ModelFile("Qwen3.5-0.8B-VL_int8.litertlm", 1303724032L, "7413f51eafb82e7cf0b707fa506eddfc76c10433b26fa8f0dc2f051970d2feb5")),
        contextTokens = 4096,
    )

    val esrgan = ModelSpec(
        id = "real-esrgan-x4v3",
        name = "Real-ESRGAN General x4v3",
        developer = "Xintao Wang et al. · conversão litert-community",
        architecture = "Rede convolucional de super-resolução (SRVGG)",
        parameters = "1,2 milhão",
        quantization = "fp16",
        categories = setOf(Category.ENHANCE),
        modalities = setOf(Modality.IMAGE),
        runtime = Runtime.LITERT,
        description = "Aumenta a resolução em 4× recuperando detalhes e limpando ruído e compressão.",
        purposes = listOf("Ampliar resolução", "Recuperar detalhes", "Limpar compressão"),
        license = "BSD 3-Clause",
        licenseUrl = "https://opensource.org/license/bsd-3-clause",
        minRamGb = 2.0,
        repo = "litert-community/real-esrgan-x4v3-litert",
        revision = "0a6fc35ba3e34e80b53f43089c1d2b612d33bea9",
        files = listOf(ModelFile("realesr_general_x4v3.tflite", 3549456L, "6d597b46d812ada42bdde641ec6e78c0ef0b78ff5a825fcfd74b9a1b3bcbe9a4")),
        published = "litert-community: ~1 ms por bloco de 128×128 na GPU (LiteRT CompiledModel).",
    )

    val iris = ModelSpec(
        id = "iris-3b-turbo",
        name = "iris-3b Turbo",
        developer = "Speridlabs · conversão litert-community",
        architecture = "Transformador de difusão em pixels + codificador Qwen3-VL-4B",
        parameters = "3 bilhões (+ codificador de 4B)",
        quantization = "int8 no transformador, fp16 no codificador",
        categories = setOf(Category.IMAGE_GEN),
        modalities = setOf(Modality.TEXT, Modality.IMAGE),
        runtime = Runtime.IRIS,
        description = "Gera imagens de 768×768 a partir de uma descrição, inteiramente no aparelho. Experimental: leva minutos por imagem.",
        purposes = listOf("Gerar imagens"),
        license = "Apache 2.0",
        licenseUrl = APACHE,
        minRamGb = 7.0,
        repo = "litert-community/iris-3b-Turbo-LiteRT",
        revision = "9444d44295f138607878403ed8e2f60082d4e06b",
        files = listOf(
            ModelFile("model/iris_te_c00_f16w.tflite", 403776672L, "f5c8a8c52d045f20b3beb131f47305bd2602848a2f105313980fa0315c3f3350"),
            ModelFile("model/iris_te_c01_f16w.tflite", 605663872L, "4a031eba17c4ca7b08ef7111b12f36a266aff21bd685f24d02749c7fd6002226"),
            ModelFile("model/iris_te_c02_f16w.tflite", 605663872L, "a4a72795a1ec889f5d717412a43eb12e69703acf099a7899776bf905b69a17b1"),
            ModelFile("model/iris_te_c03_f16w.tflite", 605663872L, "34a970242134414210023a36b1dda857818290f9c863352e56fb3f393c4befd4"),
            ModelFile("model/iris_te_c04_f16w.tflite", 605663872L, "734f496f22c5ac461503abf9df422e217a5c6387393875e9f49e2aac770f807e"),
            ModelFile("model/iris_te_c05_f16w.tflite", 605663872L, "0746bbe9371ba0ff2a44e25c129ce549a8a82613f637935aeee4ecb481a7b853"),
            ModelFile("model/iris_te_c06_f16w.tflite", 605663872L, "c9872664b1e90ee87785253258f8c1ab5de8a8e48461836cec1494a5879acf24"),
            ModelFile("model/iris_te_c07_f16w.tflite", 605663872L, "ec72b448993bbbe7aa37060a5205936d7e5d109db21ade6421b4203873616cfd"),
            ModelFile("model/iris_te_c08_f16w.tflite", 605663872L, "2f612a8ea781716b980a69dfe69e70f961ae9d24dbafb26ac740adcea301127c"),
            ModelFile("model/iris_te_c09_f16w.tflite", 605663872L, "8c8acc9b347a13da0c82e2cd6f75da13e01cdff1ef5cfaebbce12286171349a7"),
            ModelFile("model/iris_te_c10_f16w.tflite", 605663872L, "45813c0ce1be0e3c2f693f96eb901842d47316610d90a3b18918f250b9e88c58"),
            ModelFile("model/iris_te_c11_f16w.tflite", 605663872L, "977236b12c04ce0c10b9ba530db148851756c1c3f263356de103e2216f312277"),
            ModelFile("model/iris_dit_adapter_f16w_dit.tflite", 500935504L, "8519afb55b512e23168add7a2dbdc606763940b4ab09ff1817810e582252ce10"),
            ModelFile("model/iris_dit_pixel_f16w_dit.tflite", 472337056L, "964985151b58d992919d2523fd5628c441332f2019eb971b16c7a444fa408168"),
            ModelFile("model/iris_embed_bf16.f32", 777912320L, "2942e869a0df443edd3158b7f6c4f735f79faea563354b3a077db81f5f21edaa"),
            ModelFile("model/iris_host.f32", 58658832L, "139020066f8cb61ee7116b993c63d9f70957607dcbdd7167107fad76cc55737c"),
            ModelFile("model/iris_tokenizer.json", 6649288L, "dbe0ed41a0b0e0744c1fd307231cfb700003dc164330cc1ebb69fc80e8cd9fa5"),
            ModelFile("model/iris_dit_dual0_i8w_dit.tflite", 456478400L, "a56b346e7a08aa847ed4765cb7575cb56728e5cc0ac5ceeef5ac0c66b4d23e61"),
            ModelFile("model/iris_dit_dual1_i8w_dit.tflite", 454470624L, "48027e1cc2c5a450d89a49f34cc093b737407283cfbb958753d3f3de923a8c10"),
            ModelFile("model/iris_dit_dual2_i8w_dit.tflite", 302981888L, "18877cf97bf86fb3168d0339c23a1601c3e8797fdef04015a130dfeb6abdbeb1"),
            ModelFile("model/iris_dit_single0_i8w_dit.tflite", 454493456L, "ac6799d48ad92892aaa12ec58c151b22c769b3c14f989d0a8eb2d5e8add0c301"),
            ModelFile("model/iris_dit_single1_i8w_dit.tflite", 454493456L, "b2a641e294880c6dfcf43d6758c6445a1819a422c4ef87a92110aadcb2c169e1"),
            ModelFile("model/iris_dit_single2_i8w_dit.tflite", 302996704L, "913519074373888342194b84da932690cc346755ea44927a1953e468608dee45"),
            ModelFile("model/iris3b_config_dit_int8_fp16.json", 19044L, "f9eee22ff08ed94da88a2bc34d9b8ae8467def4514adb4aaaa846f026d32e676"),
        ),
        experimental = true,
        needsGpu = true,
        published = "litert-community: Galaxy S26 (int8) 128–133 s por imagem e 3,2 GiB de pico; Pixel 8a (FP32) 469–504 s.",
        notes = "Gera só 768×768 e não aceita imagem de referência. Durante a geração o Android pode fechar outros apps para liberar memória.",
    )

    // --- Vêm com o app ou com o Android ----------------------------------------------------------

    val mlkitOcr = ModelSpec(
        id = "mlkit-ocr",
        name = "Reconhecimento de texto",
        developer = "Google ML Kit",
        architecture = "Detector e reconhecedor de texto (alfabeto latino)",
        parameters = "—",
        quantization = "Modelo do ML Kit",
        categories = setOf(Category.VISION),
        modalities = setOf(Modality.IMAGE),
        runtime = Runtime.ML_KIT,
        description = "Extrai o texto de fotos, prints e documentos.",
        purposes = listOf("Extrair texto (OCR)"),
        license = "Termos do ML Kit",
        licenseUrl = "https://developers.google.com/ml-kit/terms",
        minRamGb = 2.0,
        builtIn = true,
        connection = "Não precisa: vem no app",
    )

    val mlkitLabels = mlkitOcr.copy(
        id = "mlkit-labels",
        name = "Identificação de objetos",
        architecture = "Classificador de imagens (400+ rótulos)",
        description = "Reconhece objetos, lugares, animais e atividades numa imagem.",
        purposes = listOf("Identificar objetos", "Análise visual"),
    )

    val mlkitSubject = mlkitOcr.copy(
        id = "mlkit-subject",
        name = "Recorte de objeto (remover fundo)",
        architecture = "Segmentação de assunto",
        description = "Separa a pessoa ou o objeto principal do fundo.",
        purposes = listOf("Remover fundo"),
        connection = "O Google Play baixa o modelo na primeira vez; depois funciona sem internet",
    )

    val mlkitLanguage = mlkitOcr.copy(
        id = "mlkit-language-id",
        name = "Identificação de idioma",
        architecture = "Classificador de idioma (100+ idiomas)",
        modalities = setOf(Modality.TEXT),
        categories = setOf(Category.TRANSLATE),
        description = "Diz em que idioma um texto foi escrito.",
        purposes = listOf("Identificar idioma"),
    )

    val mlkitTranslate = mlkitOcr.copy(
        id = "mlkit-translate",
        name = "Tradução no aparelho",
        architecture = "Tradução neural (pacotes por idioma)",
        modalities = setOf(Modality.TEXT),
        categories = setOf(Category.TRANSLATE),
        description = "Traduz entre português, inglês, espanhol e outros. Cada idioma é um pacote de cerca de 30 MB.",
        purposes = listOf("Tradução rápida"),
        builtIn = false,
        connection = "O ML Kit baixa o pacote do idioma na primeira tradução; depois funciona sem internet",
    )

    val androidStt = ModelSpec(
        id = "android-stt",
        name = "Reconhecimento de fala do Android",
        developer = "Google (serviço do sistema)",
        architecture = "Reconhecedor de fala do aparelho",
        parameters = "—",
        quantization = "—",
        categories = setOf(Category.SPEECH),
        modalities = setOf(Modality.AUDIO),
        runtime = Runtime.ANDROID,
        description = "Transforma o que você fala em texto. Usa o reconhecimento no aparelho quando o pacote de idioma está instalado.",
        purposes = listOf("Ditado", "Conversa por voz"),
        license = "Do sistema",
        licenseUrl = null,
        minRamGb = 2.0,
        builtIn = true,
        connection = "No aparelho com o pacote de português instalado; sem ele, o Android pode usar a rede",
    )

    val androidTts = androidStt.copy(
        id = "android-tts",
        name = "Voz do Android",
        architecture = "Síntese de voz do sistema",
        description = "Lê as respostas em voz alta com as vozes instaladas no aparelho.",
        purposes = listOf("Ler respostas", "Conversa por voz"),
        connection = "Não precisa com uma voz instalada no aparelho",
    )

    /** Tudo o que aparece no Model Hub, na ordem das prateleiras. */
    val all: List<ModelSpec> = listOf(
        qwen3Small, qwen3, gemma4, ministral, phi4,
        smolVlm, qwenVl, mlkitOcr, mlkitLabels, mlkitSubject,
        esrgan,
        iris,
        androidStt, androidTts,
        mlkitLanguage, mlkitTranslate,
    )

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }

    /** Modelos que o Model Hub baixa (os outros vêm com o app, o Android ou o ML Kit). */
    val downloadable: List<ModelSpec> get() = all.filter { it.repo != null }
}
