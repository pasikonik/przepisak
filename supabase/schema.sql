-- ===========================================================================
--  Przepisak — schemat bazy w Supabase (RF-15)
--
--  Wykonaj CAŁY ten plik raz, w SQL Editorze swojego projektu Supabase.
--  Skrypt jest idempotentny — można go uruchomić ponownie bez skutków ubocznych.
--
--  Uwaga o kształcie danych: przepis jest przechowywany jako JEDEN dokument
--  `jsonb`, a nie rozbity na tabele składników i kroków. Jest to świadoma
--  decyzja. To kopia zapasowa jednego użytkownika, więc:
--    * konflikt rozstrzyga się na poziomie całego przepisu (wygrywa nowszy
--      `updated_at`), co nie potrafi wytworzyć przepisu zlepionego z dwóch wersji,
--    * dodanie pola w aplikacji nie wymaga migracji po stronie chmury,
--    * całość mieści się w jednej tabeli i jednym zestawie polityk RLS.
--  Zapytań analitycznych po składnikach tu nie robimy — to zadanie lokalnej
--  bazy Room, która jest źródłem prawdy.
-- ===========================================================================

-- ---------------------------------------------------------------------------
--  1. Tabela przepisów
-- ---------------------------------------------------------------------------
create table if not exists public.recipes (
    -- To samo UUID, które aplikacja nadaje przepisowi lokalnie przy tworzeniu.
    id          uuid primary key,

    -- Właściciel wiersza. Domyślna wartość pozwala wstawiać bez podawania id,
    -- a polityki RLS i tak pilnują, by nie dało się pisać po cudzych danych.
    user_id     uuid        not null default auth.uid() references auth.users (id) on delete cascade,

    -- Pełny przepis (patrz RecipePayload w kodzie aplikacji).
    payload     jsonb       not null,

    -- Milisekundy epoki, nie timestamptz: porównania „co jest nowsze” robimy
    -- wtedy na liczbach i nie wchodzimy w strefy czasowe ani precyzję formatów.
    updated_at  bigint      not null,

    -- „Nagrobek”. Usunięty przepis zostaje jako wiersz, żeby usunięcie dotarło
    -- na pozostałe urządzenia, zamiast wracać przy następnym pobraniu.
    deleted     boolean     not null default false,

    created_at  timestamptz not null default now()
);

-- Synchronizacja pyta zawsze o „moje wiersze zmienione po X”.
create index if not exists recipes_user_updated_idx
    on public.recipes (user_id, updated_at);

-- ---------------------------------------------------------------------------
--  2. Bezpieczeństwo na poziomie wiersza
-- ---------------------------------------------------------------------------
alter table public.recipes enable row level security;

drop policy if exists "recipes_select_own" on public.recipes;
create policy "recipes_select_own"
    on public.recipes for select
    using (auth.uid() = user_id);

drop policy if exists "recipes_insert_own" on public.recipes;
create policy "recipes_insert_own"
    on public.recipes for insert
    with check (auth.uid() = user_id);

drop policy if exists "recipes_update_own" on public.recipes;
create policy "recipes_update_own"
    on public.recipes for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "recipes_delete_own" on public.recipes;
create policy "recipes_delete_own"
    on public.recipes for delete
    using (auth.uid() = user_id);

-- ---------------------------------------------------------------------------
--  3. Kubełek na zdjęcia
--
--  Kubełek jest PRYWATNY. Aplikacja pobiera zdjęcia na urządzenie w trakcie
--  synchronizacji (dzięki temu działają też bez internetu), więc publiczny
--  dostęp nie jest do niczego potrzebny.
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('recipe-photos', 'recipe-photos', false)
on conflict (id) do nothing;

-- Ścieżka obiektu ma postać `<remote_id przepisu>/<nazwa pliku>`, a Supabase
-- zapisuje właściciela obiektu w kolumnie `owner`. Na niej opieramy dostęp.
drop policy if exists "photos_select_own" on storage.objects;
create policy "photos_select_own"
    on storage.objects for select
    using (bucket_id = 'recipe-photos' and owner = auth.uid());

drop policy if exists "photos_insert_own" on storage.objects;
create policy "photos_insert_own"
    on storage.objects for insert
    with check (bucket_id = 'recipe-photos' and owner = auth.uid());

drop policy if exists "photos_update_own" on storage.objects;
create policy "photos_update_own"
    on storage.objects for update
    using (bucket_id = 'recipe-photos' and owner = auth.uid());

drop policy if exists "photos_delete_own" on storage.objects;
create policy "photos_delete_own"
    on storage.objects for delete
    using (bucket_id = 'recipe-photos' and owner = auth.uid());

-- ===========================================================================
--  Gotowe. Teraz w aplikacji: Ustawienia → Kopia zapasowa — wklej adres projektu
--  oraz klucz `anon public` (Project Settings → API) i utwórz konto.
-- ===========================================================================
