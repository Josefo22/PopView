package com.example.popview.activity

import com.example.popview.data.Titulo
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.popview.PopViewApp
import com.example.popview.data.Lista
import com.example.popview.adapter.PeliculasAdapter
import com.example.popview.R
import com.example.popview.data.DataStoreManager
import com.example.popview.service.PopViewAPI
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EditLista : AppCompatActivity() {
    private val peliculasList = mutableListOf<Titulo>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PeliculasAdapter
    private val popViewService = PopViewAPI().API()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_lista)
        val editTextTitulo = findViewById<EditText>(R.id.editTextTitulo)
        val editTextDescripcion = findViewById<EditText>(R.id.editTextDescripcion)
        val switchPrivada = findViewById<Switch>(R.id.switchPrivada)

        var listaData = intent.getSerializableExtra("lista") as? Lista
        Log.d("EditLista", "ID recibido: ${listaData?.id}")
        if (listaData != null) {
            editTextTitulo.setText(listaData.titulo)
            editTextDescripcion.setText(listaData.descripcion)
            switchPrivada.isChecked = listaData.esPrivada
            updateDescripcionVisibility(listaData.esPrivada, editTextDescripcion)
            // Al obtener los datos de la lista, vamos a cargar también los títulos asociados
            listaData?.let {
                // Realizamos la llamada a la API para obtener los títulos de la lista
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val titols = popViewService.getTitolsFromLlista(it.id) // Suponiendo que `getTitolsFromLlista` es un método en `PopViewAPI`
                        runOnUiThread {
                            peliculasList.clear() // Limpiamos la lista actual
                            peliculasList.addAll(titols) // Añadimos los títulos obtenidos
                            adapter.notifyDataSetChanged() // Notificamos al adaptador que los datos han cambiado
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

        } else {
            Toast.makeText(this, "No s'han rebut les dades de la llista.", Toast.LENGTH_SHORT).show()
        }

        switchPrivada.setOnCheckedChangeListener { _, isChecked ->
            updateDescripcionVisibility(isChecked, editTextDescripcion)
        }

        recyclerView = findViewById(R.id.recyclerViewPeliculas)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PeliculasAdapter(peliculasList) { titulo ->
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Confirmació")
            builder.setMessage("Segur que vols eliminar aquest títol de la llista?")
            builder.setPositiveButton("Confirmar") { dialog, _ ->
                Log.d("EditLista", "Eliminando título: ${titulo.nombre} (ID: ${titulo.id})")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        listaData?.let { lista ->
                            popViewService.deleteTituloFromLista(lista.id, titulo.id) // Llamada a la API
                            val position = peliculasList.indexOf(titulo)
                            if (position != -1) {
                                peliculasList.removeAt(position)
                                runOnUiThread {
                                    adapter.notifyItemRemoved(position)
                                }
                            }

                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(this@EditLista, "Error al eliminar el títol: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                dialog.dismiss()
            }

            builder.setNegativeButton("Cancel·lar") { dialog, _ ->
                dialog.dismiss()
            }
            builder.create().show()
        }

        recyclerView.adapter = adapter

        val btnGuardar = findViewById<Button>(R.id.btnGuardar)
        btnGuardar.setOnClickListener {
            val listaTitulo = editTextTitulo.text.toString()
            val listaDescripcion = editTextDescripcion.text.toString()
            val esPrivada = switchPrivada.isChecked

            listaData?.let { lista ->
                val updatedLista = lista.copy(
                    titulo = listaTitulo,
                    descripcion = listaDescripcion,
                    esPrivada = esPrivada,
                    titulos = peliculasList
                )
                registrarListaEditada()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Actualizar la lista existente en el servidor
                        popViewService.updateLista(lista.id, updatedLista)
                        registrarListaEditada()
                        runOnUiThread {
                            finish()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                        }
                        finish()
                    }
                }
            }
        }


        val btnEliminar = findViewById<ImageView>(R.id.btnEliminar)
        btnEliminar.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Confirmació")
            builder.setMessage("Segur que vols eliminar la llista?")
            builder.setPositiveButton("Confirmar") { dialog, _ ->
                listaData?.let { lista ->
                    // Verificamos si el ID es válido
                    if (lista.id == null || lista.id == 0) {
                        Toast.makeText(this, "No es pot eliminar la llista. ID no vàlid.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        return@setPositiveButton
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Realizamos la eliminación de la lista en el servidor
                            popViewService.deleteLista(lista.id)

                            // Si la eliminación es exitosa, hacemos las acciones correspondientes en la interfaz
                            runOnUiThread {
                                val intent = Intent()
                                intent.putExtra("eliminarLista", lista)
                                setResult(RESULT_OK, intent)
                                registrarListaEliminada()
                                finish()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            runOnUiThread {
                                Toast.makeText(this@EditLista, "Error al eliminar la llista: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                dialog.dismiss()
            }
            builder.setNegativeButton("Cancel·lar") { dialog, _ ->
                dialog.dismiss()
            }
            builder.create().show()
        }


        val btnAñadirPelicula = findViewById<Button>(R.id.btnAñadirPelicula)
        val editTextPelicula = findViewById<EditText>(R.id.editTextPelicula)

        btnAñadirPelicula.setOnClickListener {
            val peliculaTitulo = editTextPelicula.text.toString().trim()

            if (peliculaTitulo.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Obtener todos los títulos disponibles en el servidor
                        val titulos = popViewService.getAllTitols() ?: emptyList()
                        val tituloExistente = titulos.find { it.nombre.equals(peliculaTitulo, ignoreCase = true) }

                        if (tituloExistente != null) {
                            // Verifica que el título tiene todos los datos necesarios
                            if (tituloExistente.id == null || tituloExistente.nombre.isNullOrEmpty()) {
                                runOnUiThread {
                                    Toast.makeText(this@EditLista, "El títol no és vàlid.", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            registrarTituloCreado()

                            // Añadir el título a la lista en memoria
                            val position = peliculasList.size
                            peliculasList.add(tituloExistente)
                            runOnUiThread {
                                adapter.notifyItemInserted(position)
                                editTextPelicula.text.clear()
                            }
                            // Actualizar la lista en el servidor
                            listaData?.let { lista ->
                                val updatedLista = lista.copy(titulos = peliculasList)

                                // Verifica que la lista tiene todos los datos necesarios antes de actualizar
                                if (updatedLista.titulos.isNullOrEmpty() || updatedLista.titulo.isNullOrEmpty()) {
                                    runOnUiThread {
                                        Toast.makeText(this@EditLista, "La llista no té tots els camps necessaris.", Toast.LENGTH_SHORT).show()
                                    }
                                    return@launch
                                }

                                // Actualizar la lista existente en el servidor
                                popViewService.addTituloToList(lista.id, tituloExistente.id)
                                runOnUiThread {
                                    adapter.notifyDataSetChanged()
                                    editTextPelicula.text.clear()
                                }
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(this@EditLista, "El títol no existeix al servidor.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(this@EditLista, "Error al afegir el títol: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Si us plau, introdueix un títol.", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun updateDescripcionVisibility(isPrivada: Boolean, descripcionField: EditText) {
        descripcionField.visibility = if (isPrivada) EditText.GONE else EditText.VISIBLE
    }

    private fun registrarListaEditada() {
        val db = FirebaseFirestore.getInstance()
        val deviceRef = db.collection("Devices").document(PopViewApp.idDispositiu)

        deviceRef.update("listasEditadas", FieldValue.increment(1))
            .addOnSuccessListener {
                Log.d("PopViewApp", "Lista editada registrada en Devices")
            }
            .addOnFailureListener { e ->
                Log.e("PopViewApp", "Error al actualizar listas editadas en Devices", e)
            }
    }

    private fun registrarListaEliminada() {
        val db = FirebaseFirestore.getInstance()
        val deviceRef = db.collection("Devices").document(PopViewApp.idDispositiu)

        deviceRef.update("listasEliminadas", FieldValue.increment(1))
            .addOnSuccessListener {
                Log.d("PopViewApp", "Lista registrado correctamente en Devices")
            }
            .addOnFailureListener { e ->
                Log.e("PopViewApp", "Error al registrar lista en Devices", e)
            }
    }

    private fun registrarTituloCreado() {
        val db = FirebaseFirestore.getInstance()
        val deviceRef = db.collection("Devices").document(PopViewApp.idDispositiu)
        deviceRef.update("titulosCreados", FieldValue.increment(1))
            .addOnSuccessListener {
                Log.d("PopViewApp", "Titulo creado registrado en Devices")
            }
            .addOnFailureListener { e ->
                Log.e("PopViewApp", "Error al actualizar listas editadas en Devices", e)
            }
    }
    private fun registrarTituloEliminado() {
        val db = FirebaseFirestore.getInstance()
        val deviceRef = db.collection("Devices").document(PopViewApp.idDispositiu)
        deviceRef.update("titulosEliminados", FieldValue.increment(1))
            .addOnSuccessListener {
                Log.d("PopViewApp", "Titulo eliminado registrado en Devices")
            }
            .addOnFailureListener { e ->
                Log.e("PopViewApp", "Error al actualizar listas editadas en Devices", e)
            }
    }
}