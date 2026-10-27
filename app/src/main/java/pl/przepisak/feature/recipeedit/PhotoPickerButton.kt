package pl.przepisak.feature.recipeedit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.przepisak.R
import pl.przepisak.core.ui.component.RecipePhoto
import pl.przepisak.domain.model.PhotoRef

/**
 * Wybór zdjęcia z galerii albo z aparatu (RF-14).
 *
 * Korzystamy z systemowego Photo Pickera oraz `ACTION_IMAGE_CAPTURE` przez
 * FileProvider — dzięki temu aplikacja **nie potrzebuje** żadnych uprawnień do
 * pamięci ani do kamery.
 */
@Composable
fun PhotoPickerButton(
    photo: PhotoRef,
    onPicked: (Uri) -> Unit,
    onRemove: () -> Unit,
    onRequestCameraUri: suspend () -> Uri,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPicked) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) onPicked(uri)
        pendingCameraUri = null
    }

    val takePicture = {
        scope.launch {
            val uri = onRequestCameraUri()
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.aspectRatio(1f) else Modifier.aspectRatio(16f / 9f))
                .clip(RoundedCornerShape(12.dp)),
        ) {
            RecipePhoto(photo = photo, modifier = Modifier.fillMaxSize())

            if (!photo.isEmpty) {
                FilledIconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.edit_photo_remove),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                if (!compact) Text(stringResource(R.string.edit_photo_gallery))
            }
            TextButton(onClick = { takePicture() }) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                if (!compact) Text(stringResource(R.string.edit_photo_camera))
            }
        }
    }
}
