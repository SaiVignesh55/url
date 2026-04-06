import { useQuery } from "@tanstack/react-query"
import api, { cityAnalyticsApi, linksApi } from "../api/api"


export const useFetchMyShortUrls = (token, onError) => {
    return useQuery({
        queryKey: ["my-shortenurls"],
        queryFn: async () => {
            return await api.get(
                "/api/urls/myurls",
                {
                    headers: {
                        "Content-Type": "application/json",
                        Accept: "application/json",
                        Authorization: "Bearer " + token,
                    },
                }
            );
        },
        select: (data) => {
            return data.data.sort(
                (a, b) => new Date(b.createdDate) - new Date(a.createdDate)
            );
        },
        onError,
        staleTime: 5000,
    });
};

export const useFetchTotalClicks = (token, onError) => {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(endDate.getDate() - 29);

    const start = startDate.toISOString().slice(0, 10);
    const end = endDate.toISOString().slice(0, 10);

    return useQuery({
        queryKey: ["url-totalclick", start, end],
        queryFn: async () => {
            return await api.get(
                `/api/urls/totalClicks?startDate=${start}&endDate=${end}`,
                {
                    headers: {
                        "Content-Type": "application/json",
                        Accept: "application/json",
                        Authorization: "Bearer " + token,
                    },
                }
            );
        },
        select: (data) => {
            // Backward compatibility: if backend still returns a date-keyed map, normalize it.
            if (data?.data && !Array.isArray(data.data) && !data.data.clicksByDate) {
                return Object.keys(data.data).map((key) => ({
                    clickDate: key,
                    count: data.data[key],
                }));
            }

            return data?.data?.clicksByDate ?? [];
        },
        onError,
        staleTime: 5000,
    });
};

export const useFetchMyLinks = (token, onError) => {
    return useQuery({
        queryKey: ["my-links"],
        queryFn: async () => {
            const response = await linksApi.getMyLinks(token);
            return response?.data ?? [];
        },
        select: (data) => {
            if (!Array.isArray(data)) {
                return [];
            }

            return data.map((item) => ({
                shortCode: item?.shortUrl || item?.shortCode || "",
                originalUrl: item?.originalUrl || "",
            })).filter((item) => item.shortCode);
        },
        enabled: Boolean(token),
        onError,
        staleTime: 5000,
    });
};


export const useFetchCityStatsByShortCode = (token, shortCode, onError) => {
    return useQuery({
        queryKey: ["city-stats", shortCode],
        queryFn: async () => {
            const response = await cityAnalyticsApi.getCityStatsByShortCode(shortCode, token);
            return response?.data ?? [];
        },
        select: (data) => {
            if (!Array.isArray(data)) {
                return [];
            }

            return data
                .map((item) => ({
                    city: item?.city || "UNKNOWN",
                    count: Number(item?.count || 0),
                }))
                .sort((a, b) => b.count - a.count);
        },
        enabled: Boolean(token && shortCode),
        onError,
        staleTime: 5000,
    });
};
