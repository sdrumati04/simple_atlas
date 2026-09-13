package rubbertoe.simple_atlas.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import org.jspecify.annotations.NonNull;
import rubbertoe.simple_atlas.item.ModItems;

import java.util.concurrent.CompletableFuture;

public class SimpleAtlasItalianLangProvider extends FabricLanguageProvider {

    public SimpleAtlasItalianLangProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, "it_it", registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.@NonNull Provider registryLookup, TranslationBuilder translationBuilder) {
        translationBuilder.add(ModItems.ATLAS, "Atlante");
        translationBuilder.add("screen.simple_atlas.atlas.title", "Atlante");
        translationBuilder.add("gui.simple_atlas.waypoint_name", "Nome punto di passaggio");
        translationBuilder.add("gui.simple_atlas.cancel", "Annulla");
        translationBuilder.add("gui.simple_atlas.confirm", "Conferma");
        translationBuilder.add("gui.simple_atlas.waypoint.edit_title", "Modifica punto");
        translationBuilder.add("gui.simple_atlas.waypoint.new_title", "Nuovo punto");
        translationBuilder.add("gui.simple_atlas.waypoint.default_name", "Punto di passaggio");
        translationBuilder.add("menu.simple_atlas.teleport", "Teletrasportati");
        translationBuilder.add("menu.simple_atlas.copy_coordinates", "Copia coordinate");
        translationBuilder.add("menu.simple_atlas.waypoint.locate", "Individua");
        translationBuilder.add("menu.simple_atlas.waypoint.stop_locating", "Smetti di individuare");
        translationBuilder.add("menu.simple_atlas.waypoint.edit", "Modifica punto");
        translationBuilder.add("menu.simple_atlas.waypoint.delete", "Elimina punto");
        translationBuilder.add("menu.simple_atlas.map.new_waypoint", "Nuovo punto");
        translationBuilder.add("menu.simple_atlas.map.remove", "Rimuovi mappa dall'atlante");
        translationBuilder.add("message.simple_atlas.coordinates_copied", "Coordinate copiate: %s, %s");
        translationBuilder.add("message.simple_atlas.waypoint_added", "Punto aggiunto: %s");
        translationBuilder.add("message.simple_atlas.waypoint_limit_reached", "Limite punti dell'atlante raggiunto (%s)");
        translationBuilder.add("message.simple_atlas.no_maps_inserted", "Il tuo atlante non contiene mappe inserite");
        translationBuilder.add("message.simple_atlas.no_empty_maps", "Nessuna mappa vuota per espandere la mappa");
        translationBuilder.add("message.simple_atlas.map_limit_reached", "Limite mappe dell'atlante raggiunto (%s)");
        translationBuilder.add("message.simple_atlas.not_enough_paper", "Carta insufficiente per espandere la mappa (richiede %s)");
        translationBuilder.add("message.simple_atlas.transcribing_progress", "🪶 Trascrizione delle mappe in corso... (%s%%)");
        translationBuilder.add("message.simple_atlas.transcription_complete", "🪶 Trascrizione dell'atlante completata!");
        translationBuilder.add("message.simple_atlas.info_no_maps", "Nessuna mappa");
        translationBuilder.add("message.simple_atlas.info_maps", "Mappe: %s");
        translationBuilder.add("message.simple_atlas.info_empty", "Vuote: %s");
        translationBuilder.add("message.simple_atlas.info_paper", "Carta: %s");
        translationBuilder.add("tooltip.simple_atlas.transcribing", "🪶 Trascrizione in corso...");
        translationBuilder.add("tooltip.simple_atlas.no_maps", "Nessuna mappa inserita");
        translationBuilder.add("tooltip.simple_atlas.blank_maps", "Mappe vuote: %s");
        translationBuilder.add("tooltip.simple_atlas.paper", "Carta: %s");
        translationBuilder.add("tooltip.simple_atlas.scale", "Scala: (1:%s)");
        translationBuilder.add("tooltip.simple_atlas.scales", "Scale: %s");
        translationBuilder.add("gui.simple_atlas.scale_ratio", "Scala 1:%s");
        translationBuilder.add("config.simple_atlas.title", "Configurazione Simple Atlas");
        translationBuilder.add("config.simple_atlas.category.general", "Generale");
        translationBuilder.add("config.simple_atlas.max_atlas_map_count", "Numero massimo di mappe per atlante");
        translationBuilder.add("config.simple_atlas.max_atlas_map_count.tooltip", "Numero massimo di mappe che possono essere contenute in un atlante");
        translationBuilder.add("config.simple_atlas.max_waypoints", "Numero massimo di punti di passaggio per atlante");
        translationBuilder.add("config.simple_atlas.max_waypoints.tooltip", "Numero massimo di punti di passaggio che possono essere aggiunti a un atlante");
        translationBuilder.add("config.simple_atlas.banner_waypoints_only", "Solo punti di passaggio da stendardi");
        translationBuilder.add("config.simple_atlas.banner_waypoints_only.tooltip", "Se attivato, i punti possono essere creati solo usando l'atlante su uno stendardo");
        translationBuilder.add("config.simple_atlas.consume_paper_for_higher_scales", "Richiedi carta per mappe a scala elevata");
        translationBuilder.add("config.simple_atlas.consume_paper_for_higher_scales.tooltip", "Se attivato, la mappatura automatica a scale superiori a 1:1 consuma carta dalla riserva dell'atlante (1 foglio per livello di scala)");
        translationBuilder.add("config.simple_atlas.waypoint_icon_size", "Dimensione icone punti di passaggio");
        translationBuilder.add("config.simple_atlas.waypoint_icon_size.tooltip", "Fattore di scala per le icone dei punti (da 0.5 a 2.0)");
        translationBuilder.add("config.simple_atlas.player_icon_size", "Dimensione icona giocatore");
        translationBuilder.add("config.simple_atlas.player_icon_size.tooltip", "Fattore di scala per l'icona del giocatore (da 0.5 a 2.0)");
        translationBuilder.add("advancements.simple-atlas.adventure.craft_atlas.title", "Segui la carta");
        translationBuilder.add("advancements.simple-atlas.adventure.craft_atlas.description", "Ottieni un Atlante vuoto");
        translationBuilder.add("advancements.simple-atlas.adventure.old_fashioned.title", "Alla vecchia maniera");
        translationBuilder.add("advancements.simple-atlas.adventure.old_fashioned.description", "Aggiungi un punto di passaggio all'Atlante usando uno stendardo");
        translationBuilder.add("advancements.simple-atlas.adventure.backup_copy.title", "Copia di sicurezza");
        translationBuilder.add("advancements.simple-atlas.adventure.backup_copy.description", "Duplica un Atlante con un libro al tavolo da cartografia");
        translationBuilder.add("advancements.simple-atlas.adventure.better_together.title", "Meglio insieme");
        translationBuilder.add("advancements.simple-atlas.adventure.better_together.description", "Unisci due Atlanti al tavolo da cartografia");
        translationBuilder.add("advancements.simple-atlas.adventure.bigger_picture.title", "Visuale d'insieme");
        translationBuilder.add("advancements.simple-atlas.adventure.bigger_picture.description", "Aumenta la scala di un Atlante con la carta al tavolo da cartografia");
        translationBuilder.add("advancements.simple-atlas.adventure.marco.title", "Marco!");
        translationBuilder.add("advancements.simple-atlas.adventure.marco.description", "Fissa un punto di passaggio alla barra di localizzazione");
        translationBuilder.add("key.category.simple-atlas.atlas", "Simple Atlas");
        translationBuilder.add("key.simple_atlas.reset_zoom", "Reimposta zoom");
    }
}
