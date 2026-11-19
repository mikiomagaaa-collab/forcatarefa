package app.cristal.cover.data.brief

import app.cristal.cover.core.Logx
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Um arquivo JSON pequeno, gravado de forma atômica (arquivo temporário + renomear). Usado pelos
 * lembretes e pelo histórico do Info Brief, que ficam fora do banco da tela inicial.
 */
internal class JsonFileStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val json: Json,
    private val empty: T,
) {
    private val mutex = Mutex()

    suspend fun read(): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (file.exists()) json.decodeFromString(serializer, file.readText()) else empty
            } catch (t: Throwable) {
                Logx.w("Arquivo ${file.name} ilegível; começando vazio", t)
                empty
            }
        }
    }

    suspend fun write(value: T) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                tmp.writeText(json.encodeToString(serializer, value))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            } catch (t: Throwable) {
                Logx.w("Não foi possível gravar ${file.name}", t)
            }
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) { mutex.withLock { file.delete() } }
}
